# IoT Android Things Codelab - Smart Doorbell

Пройдя данный codelab, вы познакомитесь с основами разработки IoT девайсов, основанных на Android Things. Основой для codelab послужил пример [Google Cloud Doorbell](https://developer.android.com/things/training/doorbell/index.html). Codelab расчитан на участников, которые уже знакомы с разработкой под Android.

В итоге мы создадим девайс, который фотографирует всех, кто нажал дверной замок, сохраняет фотографию в Firebase и анотирует ее с помощью Google Vison, а также приложение, позволяющее посмотреть всех посетителей.

Для прохождения codelab вам понадобится:
* ноутбук с установленной Android Studio, 
* плата Raspberry Pi 3 с установленной Android Things, 
* Raspberry Pi совместимая камера,
* тактовая кнопка,
* резистор на 1кОм,
* макетная плата,
* набор соеденительных проводов.

## Шаг 0. Подготовка.

Этап установки Android things на Raspberry Pi, подключения к WiFi и подключения из Android Studio через adb connect к устройству подробно описан в [документации от Google](https://developer.android.com/things/hardware/raspberrypi.html).

## Шаг 1. Создание проекта.

До того как работать с Android Things вам необходимо обновить SDK и SDK tools до версии 24 или выше. Вы должны:

1\. Создать проект для мобильного устройства с API 24 с пустым Activity.

2\. Добавить зависимость в app-level **build.gradle**. Обратите внимание что зависимость предоставляется, а не компилируется. Это связано с тем, что для каждой Android Things совместимой платы используется своя реализация с общим интерфейсом:
```groovy
dependencies {
    ...
    provided 'com.google.android.things:androidthings:0.2-devpreview'
}
```

3\. Добавить запись об используемой библиотеке в манифест:
```xml
<application ...>
    <uses-library android:name="com.google.android.things"/>
    ...
</application>
```

4\. Добавить записи в манифест, позволяющие запускать IoT приложение при загрузке девайса (именно добавить, стандартный шntent-filter позволит запускать приложение при деплое и дебаге).
```xml
<application
    android:label="@string/app_name">
    ...
    <activity android:name=".DoorbellActivity">
        ...
        <!-- Launch activity automatically on boot -->
        <intent-filter>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.IOT_LAUNCHER"/>
            <category android:name="android.intent.category.DEFAULT"/>
        </intent-filter>
        ...
    </activity>
</application>
```

5\. После прохождения этого этапа у вас должно получится что-то [похожее](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%231).

## Шаг 2. Подключение кнопки.

В качестве дверного замка будет выступать тактовая кнопка, закрепленная на макетной плате.
Для обработки нажатий кнопки к Android Things устройству можно воспользоваться классом [Android Things SDK Gpio](https://developer.android.com/things/sdk/pio/gpio.html), но в этом случае нам придется обрабатывать дребезг кнопки, возникающий в следствии несовершенства физического мира, самостоятельно. Чтобы этого избежать воспользуемся уже готовой библиотекой для работы с кнопками. Библиотеки для работы с физическими компонентами в Android Things называются драйверы. Примеры готовых драйверов можно увидеть в [официальном репозитории Android Things](https://github.com/androidthings/contrib-drivers).

Таким образом для подключения к Android Things кнопки нам потребуется:

1\. С помощью соеденительных проводов, макетной платы, резистора и кнопки собрать следующую схему
![схема подключения кнопки к Android Things на Raspberry Pi](https://github.com/Mobilatorium/smart-doorbell-codelab/blob/master/scheme.jpg?raw=true)
**Будьте внимательны, не перепутайте выводы 3.3V и 5V, вам необходим вывод 3.3V.**

2\. Подключить зависимость драйвера в вашем build.gradle уровня приложения.
```groovy
dependencies {
    ...

    compile 'com.google.android.things.contrib:driver-button:0.2'
}
```

3\. Создать объект типа Button и инициализировать его, указав, что срабатывание происхоисходит при подаче на порт высокого уровня сигнала.

```java
import com.google.android.things.contrib.driver.button.Button;

// Access the Button and listen for events:

Button button = new Button(gpioPinName,
        // high signal indicates the button is pressed
        // use with a pull-down resistor
        Button.LogicState.PRESSED_WHEN_HIGH
);
```
gpioPinName - string аргумент, задающий название порта к которому вы подключили кнопку. Перечень всех доступных портов для Raspberry Pi вы можете увидеть на [схеме](https://developer.android.com/things/images/pinout-raspberrypi.png). 

4\. Прикрепить к созданной кнопки слушателя событий:
```java
button.setOnButtonEventListener(new OnButtonEventListener() {
    @Override
    public void onButtonEvent(Button button, boolean pressed) {
        // do something awesome
    }
});
```

5\. И не забыть освободить ресурсы, когда они нам перестануть быть нужными (в onDestroy).
```java
button.close();
```

6\. После прохождения этого этапа у вас должно получится что-то [похожее](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%232).

## Шаг 3. Подключение камеры.

Для фотографирования посетителей будет использоваться совместимая Rapsberry Pi камера. Она шлейфом подключается в специальный разъем на Raspberry Pi. **Подключение необходимо проводить к выключенной Rapsberry Pi.** 

Для взаимодействия с камерой из Android Things приложения необходимо:

1\. Указать в манифесте приложения запрос разрешение на использование камеры:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```
Обратите внимание, что зачастую у Android Things устройства нет экрана для подтверждения разрешения, поэтому все разрешения принимаются автоматически при установке приложения. На данный момент существует проблема, что для получения разрешения может потребоваться перезагрузка Raspberry Pi.

2\. Указать в манифесе приложения требования к наличию камеры:
```xml
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

3\. Создать отдельный поток для обработки операций ввода\вывода:

```java
public class DoorbellActivity extends Activity {

    /**
     * A Handler for running tasks in the background.
     */
    private Handler backgroundHandler;
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        backgroundThread.quitSafely();
    }

    /**
     * Starts a background thread and its Handler.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("InputThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
}
```

4\. Инициализировать камеру. Для этого необходимо получить список ID всех доступных камер с помощью CameraManager, создать объект **ImageReader** для обработки информации с камеры и открыть соединение с камерой. На этом этапе использются стандратные Android объекты, поэтому сложностей возникнуть не должно. Удобно выделить это в отдельный класс.

```java
public class DoorbellCamera {

    // Camera image parameters (device-specific)
    private static final int IMAGE_WIDTH  = ...;
    private static final int IMAGE_HEIGHT = ...;
    private static final int MAX_IMAGES   = ...;

    // Image result processor
    private ImageReader imageReader;
    // Active camera device connection
    private CameraDevice cameraDevice;
    // Active camera capture session
    private CameraCaptureSession captureSession;

    // Initialize a new camera device connection
    public void initializeCamera(Context context,
                                 Handler backgroundHandler,
                                 ImageReader.OnImageAvailableListener imageListener) {

        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.d(TAG, "Cam access exception getting IDs", e);
        }
        if (camIds.length < 1) {
            Log.d(TAG, "No cameras found");
            return;
        }
        String id = camIds[0];

        // Initialize image processor
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES);
        imageReader.setOnImageAvailableListener(imageListener, backgroundHandler);

        // Open the camera resource
        try {
            manager.openCamera(id, mStateCallback, backgroundHandler);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "Camera access exception", cae);
        }
    }

    private static class InstanceHolder {
        private static DoorbellCamera mCamera = new DoorbellCamera();
    }
    
    static DoorbellCamera getInstance() {
        return InstanceHolder.mCamera;
    }
    
    // Callback handling devices state changes
    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            DoorbellCamera.this.cameraDevice = cameraDevice;
        }

        ...
    };

    // Close the camera resources
    public void shutDown() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }
}
```

6\. Реализовать метод для фотографирования. Он будет вызываться по нажатию на кнопку-звонок. Сессию фотогарфирования можно реализовать с помощью **CameraCaptureSession**, в качечестве surface необходимо передать surface **ImageReader**, созданного ранее, а в **StateCallback** реагировать на успешное\не успешное конфигурирование сессии.

```java
public class DoorbellCamera {

    ...

    public void takePicture() {
        if (cameraDevice == null) {
            Log.w(TAG, "Cannot capture image. Camera not initialized.");
            return;
        }

        // Here, we create a CameraCaptureSession for capturing still images.
        try {
            cameraDevice.createCaptureSession(
                    Collections.singletonList(imageReader.getSurface()),
                    mSessionCallback,
                    null);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "access exception while preparing pic", cae);
        }
    }

    // Callback handling session state changes
    private final CameraCaptureSession.StateCallback mSessionCallback =
            new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            // When the session is ready, we start capture.
            captureSession = cameraCaptureSession;
            triggerImageCapture();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            Log.w(TAG, "Failed to configure camera");
        }
    };
}
```

7\. Реализовать метод для фотографирования. Для этого будем использовать **CaptureRequest**, surface созданого ранее **ImageReader**. По завершению фотографирования необходимо освободить ресурсы (закрыть сессию).

```java
public class DoorbellCamera {

    // Image result processor
    private ImageReader imageReader;
    // Active camera device connection
    private CameraDevice cameraDevice;
    // Active camera capture session
    private CameraCaptureSession captureSession;

    ...

    private void triggerImageCapture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.capture(captureBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "camera capture exception");
        }
    }

    // Callback handling capture progress events
    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
        new CameraCaptureSession.CaptureCallback() {
            ...

            @Override
            public void onCaptureCompleted(CameraCaptureSession session,
                                           CaptureRequest request,
                                           TotalCaptureResult result) {
                if (session != null) {
                    session.close();
                    captureSession = null;
                    Log.d(TAG, "CaptureSession closed");
                }
            }
        };
}
```

8\. Для использования фотографии c Google Cloud Vision удобно ее сериализовать, представивь в виде массива байтов. Это удобно делать в **Activity**, а не в **DoorbellCamera** классе, так как в дальнейшем эта инфомрация будет передаваться для использования с GoogleVision.

```java
public class DoorbellActivity extends Activity {

    /**
     *  Camera capture device wrapper
     */
    private DoorbellCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ...

        camera = DoorbellCamera.getInstance();
        camera.initializeCamera(this, backgroundHandler, mOnImageAvailableListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ...

        camera.shutDown();
    }

    private Button.OnButtonEventListener mButtonCallback =
            new Button.OnButtonEventListener() {
        @Override
        public void onButtonEvent(Button button, boolean pressed) {
            if (pressed) {
                // Doorbell rang!
                camera.takePicture();
            }
        }
    };

    // Callback to receive captured camera image data
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // Get the raw image bytes
            Image image = reader.acquireLatestImage();
            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();

            onPictureTaken(imageBytes);
        }
    };

    private void onPictureTaken(byte[] imageBytes) {
        if (imageBytes != null) {
            // ...process the captured image...
        }
    }
}
```

9\. На этом этапе ваше устройство уже может фотографировать. Для проверки этого можете подключить к Raspberry Pi монитор, реализовать в **main_layout** **ImageView** и отображать то, что вы сфотографировали на нем, но для прохождения codelab - это не обязательно, в дальнейшем мы будем отображать сделанный снимок на экране вспомогательного устройства.

```java
Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
imageView.setImageBitmap(bitmap);
```

10\. После прохождения этого этапа у вас должно получится что-то [похожее](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%233).

## Шаг 4. Подключение Google Cloud Vision и анализ изображения.

Google Cloud Vision предоставляет широки перечень инструментов для обработке изображений. В данном codelab мы будем использовать аннотирование с помощью меток. Это позволит нам выяснить что находится на изображении. Для работы с ним вам потребуется Cloud Vision API key. Тестовый API key вы можете получить у организаторов codelab, в случае если вы проходите codelab самостоятельно вам потребуется зарегистироваться в [Cloud Vision](https://cloud.google.com/vision/docs/quickstart), создать свой проект и [сгенерировать ключ для Android приложения](https://cloud.google.com/vision/docs/common/auth). Обратите внимание, что обработка изображения и запрос НЕ должны выполняться в главном потоке.

Далее вам необходимо:

1\. Добавить в проект на уровне приложения следующий зависимости:

```groovy
dependencies {
    ...

    compile 'com.google.api-client:google-api-client-android:1.22.0' exclude module: 'httpclient'
    compile 'com.google.http-client:google-http-client-gson:1.22.0' exclude module: 'httpclient'

    compile 'com.google.apis:google-api-services-vision:v1-rev22-1.22.0'
}
```

2\. Добавить в манифест приложения разрешение на использование интернета. Обратите внимание, что для получения разрешения может потребоваться перезагрузка Android Things устройства.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

3\. Создать **VisionRequestInitializer** с помощью полученного ранее ключа.

```java
// Construct the Vision API instance
    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    VisionRequestInitializer initializer = new VisionRequestInitializer(CLOUD_VISION_API_KEY);
```

4\. Создать новый объект **Vision**.

```java
Vision vision = new Vision.Builder(httpTransport, jsonFactory, null)
        .setVisionRequestInitializer(initializer)
        .build();
```

5\. Создать новый объект запроса **AnnotateImageRequest** и добавить к нему описание запроса с помощью объекта тап **Feature**. 

```java
// Create the image request
AnnotateImageRequest imageRequest = new AnnotateImageRequest();
Image image = new Image();
image.encodeContent(imageBytes);
imageRequest.setImage(image);

// Add the features we want
Feature labelDetection = new Feature();
labelDetection.setType("LABEL_DETECTION");
labelDetection.setMaxResults(10);
imageRequest.setFeatures(Collections.singletonList(labelDetection));
```

6\. Создать и запустить сам запрос с помощью объекта **BatchAnnotateImagesRequest**.

```java
// Batch and execute the request
BatchAnnotateImagesRequest requestBatch = new BatchAnnotateImagesRequest();
requestBatch.setRequests(Collections.singletonList(imageRequest));
BatchAnnotateImagesResponse response = vision.images()
        .annotate(requestBatch)
        .setDisableGZipContent(true)
        .execute();
```

7\. В результате выполнения запроса мы получим объект типа **BatchAnnotateImagesResponse**. Для нашей задачи нам необходимы только список аннотаций, поэтому напишем небольшой метод для конвертации из **BatchAnnotateImagesResponse** в Map<String, Float>.

```java
private Map<String, Float> convertResponseToMap(BatchAnnotateImagesResponse response) {
    Map<String, Float> annotations = new HashMap<>();

    // Convert response into a readable collection of annotations
    List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
    if (labels != null) {
        for (EntityAnnotation label : labels) {
            annotations.put(label.getDescription(), label.getScore());
        }
    }

    return annotations;
}
```

8\. Таким образом, вызвав метод, реализующий запрос к Google Vision API из созданного в преведующем шаге метода **onPictureTaken** и передав ему в качестве аргумента байтовый массив, описывающий фотографию. Мы получим список аннотаций для дальнейшего использования.

9.\ После прохождения этого этапа у вас должно получится что-то [похожее](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%234).

## Шаг 5. Сохранение информации в Firebase.

Для работы с Firebase, вам потребуется google account и созданный Firebase проект. Вы сможете это сделать из [firebase console](https://console.firebase.google.com/).

1\. Создать новый проект и добавить к проекту Android приложение. Эти операции очень просто сделать из [firebase console](https://console.firebase.google.com/).

2\. Скачать google-services.json файл и сохранить его в папке с вашим android приложением.

3\. Добавить заивисимости в **build.gradle** на уровне проекта:

```groovy
buildscript {
  dependencies {
    ...
    classpath 'com.google.gms:google-services:3.0.0'
  }
}
```

И на уровне приложения:

```groovy
dependencies {
    ...

    compile 'com.google.firebase:firebase-core:9.6.1'
    compile 'com.google.firebase:firebase-database:9.6.1'
}
```

4\. Установить разрешения для чтения\записи в базу данных. Правила доступа к БД указываются в [firebase console](https://console.firebase.google.com/) в разделе База данных - Правила. Для codelab достаточно разрешить чтение и запись из любых источников.

```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

5\. Firebase будет хранить информацию обо всех событиях нажатия на звонок. Предлагаемая структура сохраняемого эвента следующая: 

```
<doorbell-entry> {
    "image": <Base64 image data>,
    "timestamp": <event timestamp>,
    "annotations": {
        <label>: <score>,
        <label>: <score>,
        ...
    }
}
```

Для этого необходимо инциазировать объект **FirebaseDatabase** и сохранять все значения с помощью объекта **DatabaseReference**.

```java
FirebaseDatabase database;

...

database = FirebaseDatabase.getInstance();

...

private void onPictureTaken(byte[] imageBytes) {
    if (imageBytes != null) {
        try {
            // Process the image using Cloud Vision
            Map<String, Float> annotations = annotateImage(imageBytes);

            // Write the contents to Firebase
            final DatabaseReference log = database.getReference("logs").push();
            log.child("timestamp").setValue(ServerValue.TIMESTAMP);

            // Save image data Base64 encoded
            String encoded = Base64.encodeToString(imageBytes,
                    Base64.NO_WRAP | Base64.URL_SAFE);
            log.child("image").setValue(encoded);

            if (annotations != null) {
                log.child("annotations").setValue(annotations);
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to annotate image", e);
        }
    }
}
```

6\. На этом этапе вы можете видеть события нажатия на кнопку, закодированную информацию об изображении и присвоенные ему аннотации. [Примерный код на этом шаге](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%235).

## Шаг 6. Парное Android приложение.

Не у всех Android Things устройств есть устройства ввода\вывода (дисплей, клавиатура и т. д .), поэтому для отображения подробной информации о событиях и настройки устройства целесообразно использовать отдельное приложения, к примеру Android приложение. На этом шаге мы создадим Android приложение для отображения событий звонка в дверь. Для этого необходимо:

1\. Создать новый модуль в проекте и добавить firebase зависимости к модулю на уровне проекта:

```groovy
buildscript {
  dependencies {
    ...
    classpath 'com.google.gms:google-services:3.0.0'
  }
}
```

И на уровне приложения:

```groovy
dependencies {
    ...

    compile 'com.google.firebase:firebase-core:9.6.1'
    compile 'com.google.firebase:firebase-database:9.6.1'
    compile 'com.firebaseui:firebase-ui-database:0.5.3'
}
```
По сравнению с шагом № 5 мы добавили еще вспомогательную библиотеку **firebase-ui-database**, позволяющую проще отображать UI элементы, зависящие от Firebase.

2\. Добавить к новому модулю файл **google-services.json**. Обратите внимание на то, что в случае если application id будет отличаться - вам необходимо добавить новое приложение в [firebase console](https://console.firebase.google.com/)и скачать новый **google-services.json**.

3\. Для взаимодействия с **Firebase** необходимо создать класс-модель, описывающий структуру хранимого события.

```java
public class DoorbellEntry {

    Long timestamp;
    String image;
    Map<String, Float> annotations;

    public DoorbellEntry() {
    }

    public DoorbellEntry(Long timestamp, String image, Map<String, Float> annotations) {
        this.timestamp = timestamp;
        this.image = image;
        this.annotations = annotations;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getImage() {
        return image;
    }

    public Map<String, Float> getAnnotations() {
        return annotations;
    }
}
```

4\. **FirebaseRecyclerAdapter** из библиотеки **FirebaseUI** позволит очень просто отображать содержимое нашего массива событий в **Recyclerview**. По необходимости имплементация метода **populateViewHolder** будет наполнять ViewHolder для каждого из хранимых событий. 

```java
public class DoorbellEntryAdapter extends FirebaseRecyclerAdapter<DoorbellEntry, DoorbellEntryAdapter.DoorbellEntryViewHolder> {

    /**
     * ViewHolder for each doorbell entry
     */
    static class DoorbellEntryViewHolder extends RecyclerView.ViewHolder {

        public final ImageView image;
        public final TextView time;
        public final TextView metadata;

        public DoorbellEntryViewHolder(View itemView) {
            super(itemView);

            this.image = (ImageView) itemView.findViewById(R.id.imageView1);
            this.time = (TextView) itemView.findViewById(R.id.textView1);
            this.metadata = (TextView) itemView.findViewById(R.id.textView2);
        }
    }

    private Context applicationContext;

    public DoorbellEntryAdapter(Context context, DatabaseReference ref) {
        super(DoorbellEntry.class, R.layout.doorbell_entry, DoorbellEntryViewHolder.class, ref);

        applicationContext = context.getApplicationContext();
    }

    @Override
    protected void populateViewHolder(DoorbellEntryViewHolder viewHolder, DoorbellEntry model, int position) {
        // Display the timestamp
        CharSequence prettyTime = DateUtils.getRelativeDateTimeString(applicationContext,
                model.getTimestamp(), DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
        viewHolder.time.setText(prettyTime);

        // Display the image
        if (model.getImage() != null) {
            // Decode image data encoded by the Cloud Vision library
            byte[] imageBytes = Base64.decode(model.getImage(), Base64.NO_WRAP | Base64.URL_SAFE);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap != null) {
                viewHolder.image.setImageBitmap(bitmap);
            } else {
                Drawable placeholder =
                        ContextCompat.getDrawable(applicationContext, R.drawable.ic_placeholder);
                viewHolder.image.setImageDrawable(placeholder);
            }
        }

        // Display the metadata
        if (model.getAnnotations() != null) {
            ArrayList<String> keywords = new ArrayList<>(model.getAnnotations().keySet());

            int limit = Math.min(keywords.size(), 3);
            viewHolder.metadata.setText(TextUtils.join("\n", keywords.subList(0, limit)));
        } else {
            viewHolder.metadata.setText("no annotations yet");
        }
    }

}
```

5\. Создав **RecyclerView** и установив для него созданный **DoorbellEntryAdapter** мы сможем отображать сделанные фотографии и полученные с помощью Google Cloud Vision аннотации. [Пример получившегося кода](https://github.com/Mobilatorium/smart-doorbell-codelab/tree/Step%236).
