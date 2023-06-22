package org.tensorflow.lite.examples.imageclassification;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class SplashScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        /**Program her yeniden başlatıldığında 1 kere çalışır.
         2 sanıyenin sonunda uygulamanın asıl ekranına geçiş yapılır.*/
        Thread thread = new Thread(){
            @Override
            public void run(){
                try{
                    sleep(2000);
                    startActivity(new Intent(SplashScreen.this,MainActivity.class));
                    finish();

                }
                catch (Exception e){

                }
            }
        }; thread.start();
    }
}