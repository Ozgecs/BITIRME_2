package org.tensorflow.lite.examples.imageclassification.fragments;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.tensorflow.lite.examples.imageclassification.ImageClassifierHelper;
import org.tensorflow.lite.examples.imageclassification.R;
import org.tensorflow.lite.examples.imageclassification.databinding.FragmentCameraBinding;
import org.tensorflow.lite.task.vision.classifier.Classifications;

public class CameraFragment extends Fragment
        implements ImageClassifierHelper.ClassifierListener {
    private static final String TAG = "Image Classifier";
    /**Çeşitli sınıflardan nesneler*/
    private FragmentCameraBinding fragmentCameraBinding;
    private ImageClassifierHelper imageClassifierHelper;
    private Bitmap bitmapBuffer;
    private ClassificationResultAdapter classificationResultsAdapter;
    private ImageAnalysis imageAnalyzer;
    private ProcessCameraProvider cameraProvider;
    private final Object task = new Object();

    private ExecutorService cameraExecutor;

    /**Kullanıcı arayüzü kullanıcıya döndürülür
     Activity cycle */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        fragmentCameraBinding = FragmentCameraBinding
                .inflate(inflater, container, false);
        return fragmentCameraBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!PermissionsFragment.hasPermission(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(
                            CameraFragmentDirections.actionCameraToPermissions()
                    );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        cameraExecutor.shutdown();
        synchronized (task) {
            imageClassifierHelper.clearImageClassifier();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Kamera görüntülerinin işlenmesi
        cameraExecutor = Executors.newSingleThreadExecutor();
        imageClassifierHelper = ImageClassifierHelper.create(requireContext()
                , this);


        classificationResultsAdapter = new ClassificationResultAdapter();
        classificationResultsAdapter
                .updateAdapterSize(imageClassifierHelper.getMaxResults());
        fragmentCameraBinding.recyclerviewResults
                .setAdapter(classificationResultsAdapter);
        fragmentCameraBinding.recyclerviewResults
                .setLayoutManager(new LinearLayoutManager(requireContext()));

        fragmentCameraBinding.viewFinder.post(this::setUpCamera);

        initBottomSheetControls();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        imageAnalyzer.setTargetRotation(
                fragmentCameraBinding.viewFinder.getDisplay().getRotation()
        );
    }

    private void initBottomSheetControls() {
        /*Alt sayfada bulunan kontrol paneli için fonksiyonlar*/
        // Görüntü işlemede bir teknik. Binary görüntüler elde etmek
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus
                .setOnClickListener(view -> {
                    float threshold = imageClassifierHelper.getThreshold();
                    if (threshold >= 0.1) {
                        imageClassifierHelper.setThreshold(threshold - 0.1f);
                        updateControlsUi();
                    }
                });

        fragmentCameraBinding.bottomSheetLayout.thresholdPlus
                .setOnClickListener(view -> {
                    float threshold = imageClassifierHelper.getThreshold();
                    if (threshold < 0.9) {
                        imageClassifierHelper.setThreshold(threshold + 0.1f);
                        updateControlsUi();
                    }
                });


        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus
                .setOnClickListener(view -> {
                    int maxResults = imageClassifierHelper.getMaxResults();
                    if (maxResults > 1) {
                        imageClassifierHelper.setMaxResults(maxResults - 1);
                        updateControlsUi();
                        classificationResultsAdapter.updateAdapterSize(
                                imageClassifierHelper.getMaxResults()
                        );}});

        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus
                .setOnClickListener(view -> {
                    int maxResults = imageClassifierHelper.getMaxResults();
                    if (maxResults < 3) {
                        imageClassifierHelper.setMaxResults(maxResults + 1);
                        updateControlsUi();
                        classificationResultsAdapter.updateAdapterSize(
                                imageClassifierHelper.getMaxResults()
                        );
                    }
                });

        // Thread sayısı azaltma ve artırma
        fragmentCameraBinding.bottomSheetLayout.threadsMinus
                .setOnClickListener(view -> {
                    int numThreads = imageClassifierHelper.getNumThreads();
                    if (numThreads > 1) {
                        imageClassifierHelper.setNumThreads(numThreads - 1);
                        updateControlsUi();
                    }
                });

        fragmentCameraBinding.bottomSheetLayout.threadsPlus
                .setOnClickListener(view -> {
                    int numThreads = imageClassifierHelper.getNumThreads();
                    if (numThreads < 4) {
                        imageClassifierHelper.setNumThreads(numThreads + 1);
                        updateControlsUi();
                    }
                });

        // Kullanılacak olan modeli seçmek
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate
                .setSelection(0, false);
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                                               View view,
                                               int position,
                                               long id) {
                        imageClassifierHelper.setCurrentDelegate(position);
                        updateControlsUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        // no-op
                    }
                });

        fragmentCameraBinding.bottomSheetLayout.spinnerModel
                .setSelection(0, false);
        fragmentCameraBinding.bottomSheetLayout.spinnerModel
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                                               View view,
                                               int position,
                                               long id) {
                        imageClassifierHelper.setCurrentModel(position);
                        updateControlsUi();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        // no-op
                    }
                });
    }

    /** Kullanıcı arayüzü denetim güncellemesi*/
    private void updateControlsUi() {
        // Görüntü sınıflandırıcısının temizlenmesi
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue
                .setText(String.valueOf(imageClassifierHelper.getMaxResults()));
        fragmentCameraBinding.bottomSheetLayout.thresholdValue
                .setText(String.format(Locale.US, "%.2f",
                        imageClassifierHelper.getThreshold()));
        fragmentCameraBinding.bottomSheetLayout.threadsValue
                .setText(String.valueOf(imageClassifierHelper.getNumThreads()));

        synchronized (task) {
            imageClassifierHelper.clearImageClassifier();
        }
    }

    /**Kamera ayarlarının yapılandırılması */
    private void setUpCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    /**Kamera kullanım durumları */
    private void bindCameraUseCases() {

        // Arka kamerayı kullanmak için oluşturulan nesne
        CameraSelector.Builder cameraSelectorBuilder = new CameraSelector.Builder();
        CameraSelector cameraSelector = cameraSelectorBuilder
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        // En boy oranı ve çeşitli notasyonlar
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(
                        fragmentCameraBinding.viewFinder
                                .getDisplay().getRotation()
                )
                .build();

        imageAnalyzer = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.getDisplay().getRotation())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build();

        // Analizci ataması ve sınıflandırıcı metodun çağırılması
        imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
            if (bitmapBuffer == null) {
                bitmapBuffer = Bitmap.createBitmap(
                        image.getWidth(),
                        image.getHeight(),
                        Bitmap.Config.ARGB_8888);
            }
            classifyImage(image);
        });
        cameraProvider.unbindAll();
        // Önceki kamera kullanım durumlarını kaldır.
        try {
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
            );
            preview.setSurfaceProvider(
                    fragmentCameraBinding.viewFinder.getSurfaceProvider()
            );
        } catch (Exception exc) {
            Log.e(TAG, "Use case binding failed", exc);
        }
    }
    /**Görüntü verisini kopyalamak, döndürme derecesini almak ve
     buna göre bir sınıflandırma yapmak. Görüntü işleme yapılırken
     resim mutlaka bitmap formatında alınmakta. */
    private void classifyImage(@NonNull ImageProxy image) {
        bitmapBuffer.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

        int imageRotation = image.getImageInfo().getRotationDegrees();
        image.close();
        synchronized (task) {
            imageClassifierHelper.classify(bitmapBuffer, imageRotation);
        }
    }
    /**Hata mesajını kısa bir süreliğine Toast olarak
     gösterilmesi, sınıflandırma sonuçlarının temizlenmesi*/
    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            classificationResultsAdapter.updateResults(new ArrayList<>());
        });
    }

    /** Sınıflandırma sonuçlarının güncellenmesi ve
     textview a aktarılması */
    @Override
    public void onResults(List<Classifications> results, long inferenceTime) {
        requireActivity().runOnUiThread(() -> {
            classificationResultsAdapter.updateResults(results.get(0).getCategories());
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal
                    .setText(String.format(Locale.US, "%d ms", inferenceTime));
        });
    }
}
