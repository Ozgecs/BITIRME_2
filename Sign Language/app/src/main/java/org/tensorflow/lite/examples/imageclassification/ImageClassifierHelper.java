package org.tensorflow.lite.examples.imageclassification;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

/** Helper class for wrapping Image Classification actions */
public class ImageClassifierHelper {
    private static final String TAG = "ImageClassifierHelper";
    private static final int DELEGATE_CPU = 0;
    private static final int DELEGATE_GPU = 1;
    private static final int DELEGATE_NNAPI = 2;
    private static final int MY_CUSTOM_MODEL = 0;
    private static final int MY_CUSTOM_MODEL_QUANTIZED = 1;
    private static final int SIGN_MODEL = 2;

    private float threshold;
    private int numThreads;
    private int maxResults;
    private int currentDelegate;
    private int currentModel;
    private final Context context;
    private final ClassifierListener imageClassifierListener;
    private ImageClassifier imageClassifier;

    /** Helper class for wrapping Image Classification actions */
    public ImageClassifierHelper(Float threshold,
                                 int numThreads,
                                 int maxResults,
                                 int currentDelegate,
                                 int currentModel,
                                 Context context,
                                 ClassifierListener imageClassifierListener) {
        this.threshold = threshold;
        this.numThreads = numThreads;
        this.maxResults = maxResults;
        this.currentDelegate = currentDelegate;
        this.currentModel = currentModel;
        this.context = context;
        this.imageClassifierListener = imageClassifierListener;
        setupImageClassifier();
    }

    public static ImageClassifierHelper create(
            Context context,
            ClassifierListener listener
    ) {
        return new ImageClassifierHelper(
                0.5f,
                2,
                3,
                0,
                0,
                context,
                listener
        );
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public void setCurrentDelegate(int currentDelegate) {
        this.currentDelegate = currentDelegate;
    }

    public void setCurrentModel(int currentModel) {
        this.currentModel = currentModel;
    }

    private void setupImageClassifier() {
        ImageClassifier.ImageClassifierOptions.Builder optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                        .setScoreThreshold(threshold)
                        .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder =
                BaseOptions.builder().setNumThreads(numThreads);

        // CPU GPU kullanımı
        switch (currentDelegate) {
            case DELEGATE_CPU:
                break;
            case DELEGATE_GPU:
                if (new CompatibilityList().isDelegateSupportedOnThisDevice()) {
                    baseOptionsBuilder.useGpu();
                } else {
                    imageClassifierListener.onError("GPU is not supported on "
                            + "this device");
                }
                break;
            case DELEGATE_NNAPI:
                baseOptionsBuilder.useNnapi();
        }
        // model seçimi yapmak için
        /**BU KISIMDA BİZİM DÜZENLEMELERİMİZ VAR*/
        String modelName;
        switch (currentModel) {
            case MY_CUSTOM_MODEL:
                modelName = "model.tflite";
                break;
            case MY_CUSTOM_MODEL_QUANTIZED:
                modelName = "model_quant.tflite";
                break;
            case SIGN_MODEL:
                modelName = "sign.tflite";
                break;
            default:
                modelName = "mobilenetv1.tflite";
        }
        try {
            imageClassifier =
                    ImageClassifier.createFromFileAndOptions(
                            context,
                            modelName,
                            optionsBuilder.build());
        } catch (IOException e) {
            imageClassifierListener.onError("Image classifier failed to "
                    + "initialize. See error logs for details");
            Log.e(TAG, "TFLite failed to load model with error: "
                    + e.getMessage());
        }
    }

    public void classify(Bitmap image, int imageRotation) {
        if (imageClassifier == null) {
            setupImageClassifier();
        }
        // zaman damgası
        long inferenceTime = SystemClock.uptimeMillis();

        // Görüntü döndürme işlemleri, görsellerle eğitme esnasında da
        // çeşitli döndürme açıları veriliyordu, burada da verilmiş
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder().add(new Rot90Op(-imageRotation / 90)).build();
        TensorImage tensorImage =
                imageProcessor.process(TensorImage.fromBitmap(image));

        imageClassifier.classify(tensorImage);

        List<Classifications> result = imageClassifier.classify(tensorImage);

        // sınıflandırma işleminin toplam süresi
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        imageClassifierListener.onResults(result, inferenceTime);
    }

    public void clearImageClassifier() {
        imageClassifier = null;
    }

    /** Sonuçları çağıran sınıfa iletmek için bir dinleyici  */
    public interface ClassifierListener {
        void onError(String error);

        void onResults(List<Classifications> results, long inferenceTime);
    }
}
