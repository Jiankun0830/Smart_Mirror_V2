package com.arcsoft.sdk_demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.arcsoft.ageestimation.ASAE_FSDKAge;
import com.arcsoft.ageestimation.ASAE_FSDKEngine;
import com.arcsoft.ageestimation.ASAE_FSDKError;
import com.arcsoft.ageestimation.ASAE_FSDKFace;
import com.arcsoft.ageestimation.ASAE_FSDKVersion;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;
import com.arcsoft.genderestimation.ASGE_FSDKEngine;
import com.arcsoft.genderestimation.ASGE_FSDKError;
import com.arcsoft.genderestimation.ASGE_FSDKFace;
import com.arcsoft.genderestimation.ASGE_FSDKGender;
import com.arcsoft.genderestimation.ASGE_FSDKVersion;
import com.arcsoft.sdk_demo.DataUpdater.UpdateListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.guo.android_extend.GLES2Render;
import com.guo.android_extend.java.AbsLoop;
import com.guo.android_extend.java.ExtByteArrayOutputStream;
import com.guo.android_extend.tools.CameraHelper;
import com.guo.android_extend.widget.CameraFrameData;
import com.guo.android_extend.widget.CameraGLSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView.OnCameraListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//import com.google.firebase.database.FirebaseDatabase;

public class DetecterActivity extends Activity implements OnCameraListener, View.OnTouchListener, Camera.AutoFocusCallback, View.OnClickListener {
	private final String TAG = this.getClass().getSimpleName();

	private int mWidth, mHeight, mFormat;
	private CameraSurfaceView mSurfaceView;
	private CameraGLSurfaceView mGLSurfaceView;
	private Camera mCamera;

	AFT_FSDKVersion version = new AFT_FSDKVersion();
	AFT_FSDKEngine engine = new AFT_FSDKEngine();
	ASAE_FSDKVersion mAgeVersion = new ASAE_FSDKVersion();
	ASAE_FSDKEngine mAgeEngine = new ASAE_FSDKEngine();
	ASGE_FSDKVersion mGenderVersion = new ASGE_FSDKVersion();
	ASGE_FSDKEngine mGenderEngine = new ASGE_FSDKEngine();
	List<AFT_FSDKFace> result = new ArrayList<>();
	List<ASAE_FSDKAge> ages = new ArrayList<>();
	List<ASGE_FSDKGender> genders = new ArrayList<>();

	int mCameraID;
	int mCameraRotate;
	int mCameraMirror;
	byte[] mImageNV21 = null;
	FRAbsLoop mFRAbsLoop = null;
	AFT_FSDKFace mAFT_FSDKFace = null;
	Handler mHandler;
	boolean isPostted = false;

	Runnable hide = new Runnable() {
		@Override
		public void run() {
			mTextView.setAlpha(0.5f);
			mImageView.setImageAlpha(128);
			isPostted = false;
		}
	};

	class FRAbsLoop extends AbsLoop {

		AFR_FSDKVersion version = new AFR_FSDKVersion();
		AFR_FSDKEngine engine = new AFR_FSDKEngine();
		AFR_FSDKFace result = new AFR_FSDKFace();
		List<FaceDB.FaceRegist> mResgist = ((Application)DetecterActivity.this.getApplicationContext()).mFaceDB.mRegister;
		List<ASAE_FSDKFace> face1 = new ArrayList<>();
		List<ASGE_FSDKFace> face2 = new ArrayList<>();
		
		@Override
		public void setup() {
			AFR_FSDKError error = engine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
			Log.d(TAG, "AFR_FSDK_InitialEngine = " + error.getCode());
			error = engine.AFR_FSDK_GetVersion(version);
			Log.d(TAG, "FR=" + version.toString() + "," + error.getCode()); //(210, 178 - 478, 446), degree = 1　780, 2208 - 1942, 3370
		}

		@Override
		public void loop() {
			if (mImageNV21 != null) {
				final int rotate = mCameraRotate;

				long time = System.currentTimeMillis();
				AFR_FSDKError error = engine.AFR_FSDK_ExtractFRFeature(mImageNV21, mWidth, mHeight, AFR_FSDKEngine.CP_PAF_NV21, mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree(), result);
				Log.d(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
				Log.d(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + error.getCode());
				AFR_FSDKMatching score = new AFR_FSDKMatching();
				float max = 0.0f;
				String name = null;
				for (FaceDB.FaceRegist fr : mResgist) {
					for (AFR_FSDKFace face : fr.mFaceList.values()) {
						error = engine.AFR_FSDK_FacePairMatching(result, face, score);
						Log.d(TAG,  "Score:" + score.getScore() + ", AFR_FSDK_FacePairMatching=" + error.getCode());
						if (max < score.getScore()) {
							max = score.getScore();
							name = fr.mName;
						}
					}
				}

				//age & gender
				face1.clear();
				face2.clear();
				face1.add(new ASAE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
				face2.add(new ASGE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
				ASAE_FSDKError error1 = mAgeEngine.ASAE_FSDK_AgeEstimation_Image(mImageNV21, mWidth, mHeight, AFT_FSDKEngine.CP_PAF_NV21, face1, ages);
				ASGE_FSDKError error2 = mGenderEngine.ASGE_FSDK_GenderEstimation_Image(mImageNV21, mWidth, mHeight, AFT_FSDKEngine.CP_PAF_NV21, face2, genders);
				Log.d(TAG, "ASAE_FSDK_AgeEstimation_Image:" + error1.getCode() + ",ASGE_FSDK_GenderEstimation_Image:" + error2.getCode());
				Log.d(TAG, "age:" + ages.get(0).getAge() + ",gender:" + genders.get(0).getGender());
				final String age = ages.get(0).getAge() == 0 ? "Unknown Age" : ages.get(0).getAge() + "years old";
				final String gender = genders.get(0).getGender() == -1 ? "Unknown gender" : (genders.get(0).getGender() == 0 ? "Male" : "Female");
				
				//crop
				byte[] data = mImageNV21;
				YuvImage yuv = new YuvImage(data, ImageFormat.NV21, mWidth, mHeight, null);
				ExtByteArrayOutputStream ops = new ExtByteArrayOutputStream();
				yuv.compressToJpeg(mAFT_FSDKFace.getRect(), 80, ops);
				final Bitmap bmp = BitmapFactory.decodeByteArray(ops.getByteArray(), 0, ops.getByteArray().length);
				try {
					ops.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (max > 0.6f) {
					//fr success.
					final float max_score = max;
					Log.d(TAG, "fit Score:" + max + ", NAME:" + name);
					final String mNameShow = name;
					mHandler.removeCallbacks(hide);
					mHandler.post(new Runnable() {
						@Override
						public void run() {
							mTextView.setAlpha(1.0f);
							mTextView.setText(mNameShow);
							mTextView.setTextColor(Color.WHITE);
							mTextView1.setVisibility(View.VISIBLE);
							mTextView1.setText("Confidence：" + (float)((int)(max_score * 1000)) / 1000.0);
							mTextView1.setTextColor(Color.WHITE);
							mImageView.setRotation(rotate);
							mImageView.setScaleY(-mCameraMirror);
							mImageView.setImageAlpha(255);
							mImageView.setImageBitmap(bmp);

							//firebase info extraction
							//TODO: Use a FirebaseUpdater to update information from firebase, solve the bugs met

							mReference = FirebaseDatabase.getInstance().getReference();
							DatabaseReference schoolEvent = mReference.child("schoolEvent");
							schoolEvent.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									keys.clear();
									values.clear();
									for(DataSnapshot ds : dataSnapshot.getChildren()){
										Log.i(TAG, ds.getKey());
										Log.i(TAG, (String) ds.getValue()) ;

										keys.add(ds.getKey());
										values.add(ds.getValue(String.class));


									}
									j = 0;
									for (int i = 5; i < REMINDERS_VIEW_IDS.length - 1; i++) {
										if ((values != null)&&(j < values.size())) {
											reminderViews[i].setText("School Event: "+keys.get(j)+" "+values.get(j));
											reminderViews[i].setVisibility(View.VISIBLE);
										} else {
											reminderViews[i].setVisibility(View.GONE);
										}
									}
								}

								@Override
								public void onCancelled(DatabaseError databaseError) {

								}
							});
							/*
							mReference.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									keys.clear();
									values.clear();
									for(DataSnapshot ds : dataSnapshot.getChildren()){
										Log.i(TAG, ds.getKey());
										Log.i(TAG, (String) ds.getValue()) ;

										keys.add(ds.getKey());
										values.add(ds.getValue(String.class));


									}
									index = keys.indexOf("name");
									reminderViews[0].setText("Good day, " + values.get(index));
									j = 1;
									for (int i = 0; i < values.size(); i++) {
										if ((values.get(i) != null) && (j < REMINDERS_VIEW_IDS.length - 1)&&(!keys.get(i).equals("name"))) {

											reminderViews[j].setText(keys.get(i)+" : "+values.get(i));
											reminderViews[j].setVisibility(View.VISIBLE);
											j++;
										}
									}
								}

								@Override
								public void onCancelled(DatabaseError databaseError) {

								}
							});
							*/




							DatabaseReference id = mReference.child(mNameShow).child("name");
							DatabaseReference HW = mReference.child(mNameShow).child("HWDue");
							DatabaseReference exam = mReference.child(mNameShow).child("examDate");
							DatabaseReference self = mReference.child(mNameShow).child("self");
							DatabaseReference project = mReference.child(mNameShow).child("project");

							id.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									String value = dataSnapshot.getValue(String.class);
									reminderViews[0].setText("Good day, " + value);
									Log.d(TAG, "Value is: " + value);
								}

								@Override
								public void onCancelled(DatabaseError databaseError){}
							});

							self.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									String value = dataSnapshot.getValue(String.class);
									reminderViews[1].setText("Personal reminder: " + value);
									Log.d(TAG, "Value is: " + value);
								}

								@Override
								public void onCancelled(DatabaseError databaseError){}
							});

							HW.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									String value = dataSnapshot.getValue(String.class);
									reminderViews[3].setText("Recent Homework Due:" + value);
									Log.d(TAG, "Value is: " + value);
								}

								@Override
								public void onCancelled(DatabaseError databaseError){}
							});

							exam.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									String value = dataSnapshot.getValue(String.class);
									reminderViews[2].setText("Next Exam Date: " + value);
									Log.d(TAG, "Value is: " + value);
								}

								@Override
								public void onCancelled(DatabaseError databaseError){}
							});

							project.addValueEventListener(new ValueEventListener() {
								@Override
								public void onDataChange(DataSnapshot dataSnapshot) {
									String value = dataSnapshot.getValue(String.class);
									reminderViews[4].setText("Project Due: " + value);
									Log.d(TAG, "Value is: " + value);
								}

								@Override
								public void onCancelled(DatabaseError databaseError){}
							});


						}
					});
				} else {
					final String mNameShow = "Unrecognized";
					DetecterActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mTextView.setAlpha(1.0f);
							mTextView1.setVisibility(View.VISIBLE);
							mTextView1.setText( gender + "," + age);
							mTextView1.setTextColor(Color.RED);
							mTextView.setText(mNameShow);
							mTextView.setTextColor(Color.RED);
							mImageView.setImageAlpha(255);
							mImageView.setRotation(rotate);
							mImageView.setScaleY(-mCameraMirror);
							mImageView.setImageBitmap(bmp);
						}
					});
				}
				mImageNV21 = null;
			}

		}

		@Override
		public void over() {
			AFR_FSDKError error = engine.AFR_FSDK_UninitialEngine();
			Log.d(TAG, "AFR_FSDK_UninitialEngine : " + error.getCode());
		}
	}

	//mirror UI
	private TextView mTextView;
	private TextView mTextView1;
	private ImageView mImageView;
	private ImageButton mImageButton;
	private TextView temperatureView;
	private TextView weatherSummaryView;
	private TextView precipitationView;
	private ImageView iconView;
	private Util util;
	private WeatherUpdater weather;
	private TextView[] reminderViews = new TextView[REMINDERS_VIEW_IDS.length];
	private static final int[] REMINDERS_VIEW_IDS = new int[]{
			R.id.reminder_1,
			R.id.reminder_2,
			R.id.reminder_3,
			R.id.reminder_4,
			R.id.reminder_5,
			R.id.event_6,
			R.id.news_7,
	};
	private NewsUpdater news;

	private DatabaseReference mReference;

	private ArrayList<String> keys = new ArrayList<>();
	private ArrayList<String> values = new ArrayList<>();
	private String faceName;
	int index = 0;
	int j = 0;




	private final UpdateListener<List<String>> newsUpdateListener =
			new UpdateListener<List<String>>() {
				@Override
				public void onUpdate(List<String> headlines) {

					// Populate the views with as many headlines as we have and hide the others.

					for (int i = 6; i < REMINDERS_VIEW_IDS.length; i++) {
						if ((headlines != null) && (i < headlines.size())) {
							reminderViews[i].setText("Daily News: "+headlines.get(i));
							reminderViews[i].setVisibility(View.VISIBLE);
						} else {
							reminderViews[i].setVisibility(View.GONE);
						}
					}

				}
			};

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	private double getLocalizedTemperature(double temperatureFahrenheit) {
		// First approximation: Fahrenheit for US and Celsius anywhere else.
		return Locale.US.equals(Locale.getDefault()) ?
				temperatureFahrenheit : (temperatureFahrenheit - 32.0) / 1.8;
	}

	private final DataUpdater.UpdateListener<WeatherUpdater.WeatherData> weatherUpdateListener =
			new DataUpdater.UpdateListener<WeatherUpdater.WeatherData>() {
				@Override
				public void onUpdate(WeatherUpdater.WeatherData data) {
					if (data != null) {

						// Populate the current temperature rounded to a whole number.
						String temperature = String.format(Locale.US, "%d°",
								Math.round(getLocalizedTemperature(data.currentTemperature)));
						temperatureView.setText(temperature);

						// Populate the 24-hour forecast summary, but strip any period at the end.
						String summary = util.stripPeriod(data.daySummary);
						weatherSummaryView.setText(summary);

						// Populate the precipitation probability as a percentage rounded to a whole number.
						String precipitation =
								String.format(Locale.US, "%d%%", Math.round(100 * data.dayPrecipitationProbability));
						precipitationView.setText(precipitation);

						// Populate the icon for the current weather.
						iconView.setImageResource(data.currentIcon);

						// Show all the views.
						temperatureView.setVisibility(View.VISIBLE);
						weatherSummaryView.setVisibility(View.VISIBLE);
						precipitationView.setVisibility(View.VISIBLE);
						iconView.setVisibility(View.VISIBLE);
					} else {

						// Hide everything if there is no data.
						temperatureView.setVisibility(View.GONE);
						weatherSummaryView.setVisibility(View.GONE);
						precipitationView.setVisibility(View.GONE);
						iconView.setVisibility(View.GONE);
					}
				}
			};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		mCameraID = getIntent().getIntExtra("Camera", 0) == 0 ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
		mCameraRotate = getIntent().getIntExtra("Camera", 0) == 0 ? 90 : 270;
		mCameraMirror = getIntent().getIntExtra("Camera", 0) == 0 ? GLES2Render.MIRROR_NONE : GLES2Render.MIRROR_X;
		mWidth = 1280;
		mHeight = 960;
		mFormat = ImageFormat.NV21;
		mHandler = new Handler();

		setContentView(R.layout.activity_camera);
		mGLSurfaceView = (CameraGLSurfaceView) findViewById(R.id.glsurfaceView);
		mGLSurfaceView.setOnTouchListener(this);
		mSurfaceView = (CameraSurfaceView) findViewById(R.id.surfaceView);
		mSurfaceView.setOnCameraListener(this);
		mSurfaceView.setupGLSurafceView(mGLSurfaceView, true, mCameraMirror, mCameraRotate);
		mSurfaceView.debug_print_fps(true, false);

		//snap
		mTextView = (TextView) findViewById(R.id.textView);
		mTextView.setText("");
		mTextView1 = (TextView) findViewById(R.id.textView1);
		mTextView1.setText("");

		mImageView = (ImageView) findViewById(R.id.imageView);
		//mImageButton = (ImageButton) findViewById(R.id.imageButton);
		//mImageButton.setOnClickListener(this);

		AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
		Log.d(TAG, "AFT_FSDK_InitialFaceEngine =" + err.getCode());
		err = engine.AFT_FSDK_GetVersion(version);
		Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());

		ASAE_FSDKError error = mAgeEngine.ASAE_FSDK_InitAgeEngine(FaceDB.appid, FaceDB.age_key);
		Log.d(TAG, "ASAE_FSDK_InitAgeEngine =" + error.getCode());
		error = mAgeEngine.ASAE_FSDK_GetVersion(mAgeVersion);
		Log.d(TAG, "ASAE_FSDK_GetVersion:" + mAgeVersion.toString() + "," + error.getCode());

		ASGE_FSDKError error1 = mGenderEngine.ASGE_FSDK_InitgGenderEngine(FaceDB.appid, FaceDB.gender_key);
		Log.d(TAG, "ASGE_FSDK_InitgGenderEngine =" + error1.getCode());
		error1 = mGenderEngine.ASGE_FSDK_GetVersion(mGenderVersion);
		Log.d(TAG, "ASGE_FSDK_GetVersion:" + mGenderVersion.toString() + "," + error1.getCode());

		mFRAbsLoop = new FRAbsLoop();
		mFRAbsLoop.start();

		temperatureView = (TextView) findViewById(R.id.temperature);
		weatherSummaryView = (TextView) findViewById(R.id.weather_summary);
		precipitationView = (TextView) findViewById(R.id.precipitation);
		iconView = (ImageView) findViewById(R.id.icon);

		for (int i = 0; i < REMINDERS_VIEW_IDS.length; i++) {
			reminderViews[i] = (TextView) findViewById(REMINDERS_VIEW_IDS[i]);
		}

		weather = new WeatherUpdater(this, weatherUpdateListener);
		news = new NewsUpdater(newsUpdateListener);
		util = new Util(this);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mFRAbsLoop.shutdown();
		AFT_FSDKError err = engine.AFT_FSDK_UninitialFaceEngine();
		Log.d(TAG, "AFT_FSDK_UninitialFaceEngine =" + err.getCode());

		ASAE_FSDKError err1 = mAgeEngine.ASAE_FSDK_UninitAgeEngine();
		Log.d(TAG, "ASAE_FSDK_UninitAgeEngine =" + err1.getCode());

		ASGE_FSDKError err2 = mGenderEngine.ASGE_FSDK_UninitGenderEngine();
		Log.d(TAG, "ASGE_FSDK_UninitGenderEngine =" + err2.getCode());
	}

	@Override
	public Camera setupCamera() {
		// TODO Auto-generated method stub
		mCamera = Camera.open(mCameraID);
		try {
			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mWidth, mHeight);
			parameters.setPreviewFormat(mFormat);

			for( Camera.Size size : parameters.getSupportedPreviewSizes()) {
				Log.d(TAG, "SIZE:" + size.width + "x" + size.height);
			}
			for( Integer format : parameters.getSupportedPreviewFormats()) {
				Log.d(TAG, "FORMAT:" + format);
			}

			List<int[]> fps = parameters.getSupportedPreviewFpsRange();
			for(int[] count : fps) {
				Log.d(TAG, "T:");
				for (int data : count) {
					Log.d(TAG, "V=" + data);
				}
			}
			//parameters.setPreviewFpsRange(15000, 30000);
			//parameters.setExposureCompensation(parameters.getMaxExposureCompensation());
			//parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
			//parameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
			//parmeters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			//parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
			//parameters.setColorEffect(Camera.Parameters.EFFECT_NONE);
			mCamera.setParameters(parameters);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (mCamera != null) {
			mWidth = mCamera.getParameters().getPreviewSize().width;
			mHeight = mCamera.getParameters().getPreviewSize().height;
		}
		return mCamera;
	}

	@Override
	public void setupChanged(int format, int width, int height) {

	}

	@Override
	public boolean startPreviewImmediately() {
		return true;
	}

	@Override
	public Object onPreview(byte[] data, int width, int height, int format, long timestamp) {
		AFT_FSDKError err = engine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
		Log.d(TAG, "AFT_FSDK_FaceFeatureDetect =" + err.getCode());
		Log.d(TAG, "Face=" + result.size());
		for (AFT_FSDKFace face : result) {
			Log.d(TAG, "Face:" + face.toString());
		}
		if (mImageNV21 == null) {
			if (!result.isEmpty()) {
				mAFT_FSDKFace = result.get(0).clone();
				mImageNV21 = data.clone();
			} else {
				if (!isPostted) {
					mHandler.removeCallbacks(hide);
					mHandler.postDelayed(hide, 2000);
					isPostted = true;
				}
			}
		}
		//copy rects
		Rect[] rects = new Rect[result.size()];
		for (int i = 0; i < result.size(); i++) {
			rects[i] = new Rect(result.get(i).getRect());
		}
		//clear result.
		result.clear();
		//return the rects for render.
		return rects;
	}

	@Override
	public void onBeforeRender(CameraFrameData data) {

	}

	@Override
	public void onAfterRender(CameraFrameData data) {
		mGLSurfaceView.getGLES2Render().draw_rect((Rect[])data.getParams(), Color.GREEN, 2);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		CameraHelper.touchFocus(mCamera, event, v, this);
		return false;
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success) {
			Log.d(TAG, "Camera Focus SUCCESS!");
		}
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.imageButton) {
			if (mCameraID == Camera.CameraInfo.CAMERA_FACING_BACK) {
				mCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;
				mCameraRotate = 270;
				mCameraMirror = GLES2Render.MIRROR_X;
			} else {
				mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
				mCameraRotate = 90;
				mCameraMirror = GLES2Render.MIRROR_NONE;
			}
			mSurfaceView.resetCamera();
			mGLSurfaceView.setRenderConfig(mCameraRotate, mCameraMirror);
			mGLSurfaceView.getGLES2Render().setViewDisplay(mCameraMirror, mCameraRotate);
		}
	}


	@Override
	protected void onStart() {
		super.onStart();
		weather.start();
		news.start();
	}

	@Override
	protected void onStop() {
		weather.stop();
		news.stop();
		super.onStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		util.hideNavigationBar(temperatureView);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return util.onKeyUp(keyCode, event);
	}

}
