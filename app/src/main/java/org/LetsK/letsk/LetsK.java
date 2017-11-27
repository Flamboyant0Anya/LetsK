package org.LetsK.letsk;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

import org.LetsK.record.myWavRecorder;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class LetsK extends Activity {

	final StringBuffer sb = new StringBuffer();
	String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();//????
	String projectPath = sdDir + "/LetsK";
	String loadUrlPic_portrait = "android-robot-icon3.jpg";
	String loadUrlPic_landscape = "android-robot-icon2.jpg";
	String webLoadPic_portrait = "loading.jpg";
	String webLoadPic_landscape = "loading_landscape.jpg";
	String wavFilePath = projectPath + "/recorder.wav";
	String bgmusicPath=projectPath+"/BGM.wav";
	File wavFile;

	TextView tv,tvpitch,tvword;
	ImageButton recordBtn, playBtn;
	myWavRecorder recorderInstance;
	Thread recordThread, playThread, recogThread, stopThread, wordregThread,progressThread;
	MediaPlayer mMediaPlayer;
	ProgressBar pgb;
	boolean isplay,ispaused,isrecording;

	// 用HashMap存储听写结果
	private HashMap<String, String> mIatResults = new LinkedHashMap<String , String>();
	private SpeechRecognizer mIat;

	protected static final int RECORDING = 101;
	protected static final int REC_FINISH = 102;
	protected static final int RECOG = 103;
	protected static final int RECOG_FINISH = 104;
	protected static final int PLAY = 105;
	protected static final int PLAY_FINISH = 106;
	protected static final int STOP = 107;
	protected static final int WORDRECOG_FINISH = 108;

	private final Object mutex = new Object();

	OnClickListener btnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			if (v.getId() == R.id.record) {
				if (!isrecording) {
					isrecording=true;
					playBtn.setEnabled(false);
					recordThread = new Thread(new RecordThread());
					recordThread.start();
					playThread = new Thread(new PlayThread(bgmusicPath));
					playThread.start();
				}
				else{
					isrecording=false;
					recorderInstance.setRecording(false);
					Message m = new Message();
					m.what=REC_FINISH;
					myMessageHandler.sendMessage(m);

				}
			}
			if (v.getId() == R.id.play) {
				if (wavFile.exists()) {
					if (!isplay) {
						Message m = new Message();
						m.what = PLAY;
						myMessageHandler.sendMessage(m);
						recordBtn.setEnabled(false);
						isplay=true;
						playThread = new Thread(new PlayThread(wavFilePath));
						playThread.start();
					}
					else {
						Message m = new Message();
						m.what = STOP;
						myMessageHandler.sendMessage(m);
						recordBtn.setEnabled(true);
						playBtn.setEnabled(true);
						isplay=false;
						if (playThread!=null) playThread.interrupt();
						stopThread = new Thread(new stopThread());
						stopThread.start();
					}
				} else {
					tv.setText("Error");
				}
			}
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		SpeechUtility.createUtility(this, SpeechConstant. APPID + "=59d5dc3d" );
		try {
			if(!new File(sdDir+"/LetsK").exists()) {
				new File(sdDir+"/LetsK").mkdir();
			}

			if(!new File(sdDir + "/LetsK/images").exists()){
				new File(sdDir + "/LetsK/images").mkdir();
			}
			CopyFromAssets(loadUrlPic_portrait, projectPath + "/" + loadUrlPic_portrait);
			CopyFromAssets(loadUrlPic_landscape, projectPath + "/" + loadUrlPic_landscape);
			CopyFromAssets(webLoadPic_portrait, projectPath + "/" + webLoadPic_portrait);	        //
			CopyFromAssets(webLoadPic_landscape, projectPath + "/" + webLoadPic_landscape);
		} catch (Exception e) {
			Log.e("Exception", e.toString());
		}

		tv = (TextView) findViewById(R.id.tv);
		tvpitch=(TextView) findViewById(R.id.tvpitch);
		tvword=(TextView) findViewById(R.id.tvword);
		recordBtn = (ImageButton) findViewById(R.id.record);
		playBtn = (ImageButton) findViewById(R.id.play);
		mMediaPlayer = new MediaPlayer();
		pgb=(ProgressBar) findViewById(R.id.progressbar);

		isplay=false;
		ispaused=false;
		isrecording=false;

		recordBtn.setOnClickListener(btnListener);
		playBtn.setOnClickListener(btnListener);

		wavFile = new File(wavFilePath);
		if (wavFile.exists()) {wavFile.delete();}
	}

	Handler myMessageHandler = new Handler(){
		public void handleMessage(Message msg){
			switch(msg.what){
				case RECORDING:
					if(!recordThread.isInterrupted()){
						tv.setText("recording");
					}
					break;
				case REC_FINISH:
					recordThread.interrupt();
					recordThread = null;
					if (playThread!=null) {
						playThread.interrupt();
						playThread = null;
					}
					tv.setText("recorded");
					recordBtn.setEnabled(true);
					playBtn.setEnabled(true);
					Message m = new Message();
					m.what=RECOG;
					myMessageHandler.sendMessage(m);
					recogThread = new Thread(new RecogThread());
					recogThread.start();

					break;
				case PLAY:
					if(!playThread.isInterrupted()){
						tv.setText("playing");
					}
					break;
				case STOP:
					tv.setText("stop");break;
				case PLAY_FINISH:
					if (playThread!=null) playThread.interrupt();
					playThread = null;
					recordBtn.setEnabled(true);
					playBtn.setEnabled(true);
					tv.setText("played");
					break;
				case RECOG:
					if(!recogThread.isInterrupted()){
						tv.setText("recognizing");
					}
					break;
				case RECOG_FINISH:
					tv.setText("recognized");
					tvpitch.setText(sb);
					recogThread.interrupt();
					recogThread = null;

					break;
				case WORDRECOG_FINISH:break;
			}
			super.handleMessage(msg);
		}
	};

	private void goRecog() {
		try{
			synchronized (mutex){
				while (recorderInstance.flag==false) Thread.sleep(50);
			}
			while(!wavFile.exists()) {wavFile = new File(wavFilePath);}
			FileInputStream fileIS = new FileInputStream(wavFile);

			int wav_len = (int) wavFile.length();
			// 44Byte header of WAV file, which is 22 short integer long
			int offset = 22;
			// frame number
			int frame_num = (int)((wav_len / 2 - offset) / 512);
			byte[] buffer = new byte[wav_len];
			fileIS.read(buffer, 0, wav_len);

			wordregThread=new Thread (new WordRecThread());
			wordregThread.start();

			int[] y = new int[wav_len/2 - offset];
			int[] vol = new int[frame_num];

			int fs = 16000;
			int frameDuration=32;	// in ms
			int frameSize = Math.round(frameDuration*fs/1000);
			int overlap = 0;
			int maxShift=frameSize;
			//range of Hz from people
			int maxFreq=900;
			int minFreq=60;
			// acf(1:n1) will not be used
			int n1 = Math.round(fs/maxFreq);
			// acf(n2:end) will not be used
			int n2 = Math.round(fs/minFreq);
			int[][] frameMat = new int[wav_len/2/frameSize][frameSize];
			int volumeTh;
			int[] temp_frame = new int[frameSize];
			int maxValue = 0;
			int[] acf = new int[maxShift];
			int[] pitch = new int[frame_num];

			for(int i = 0; i < wav_len/2 - offset;i++){
				y[i] = byte2int(buffer[2*i +2*offset],buffer[2*i+1 +2*offset]);
			}
			frameMat=buffer2(y, frameSize, overlap);
			int frameNum =frameMat.length;

			for(int j = 0; j < frame_num; j++) {
				vol[j] = 0;
				for(int i = 0; i < 512; i++){
					vol[j] += Math.abs(y[j*512 + i]);
				}
				if (vol[j] > maxValue){
					maxValue = vol[j];
				}
			}
			volumeTh = maxValue / 8;
			pitch[0] = 0;
			for (int i = 1;i<frameNum;i++) {
				if (vol[i] > volumeTh & vol[i-1] > volumeTh ){
					for(int j = 0;j<frameSize;j++){
						temp_frame[j] = frameMat[i][j];
					}
					acf = frame2acf(temp_frame, maxShift,n1,n2, 1);
					int maxIndex_acf = n1;
					maxValue = acf[n1];

					for(int l = n1; l < n2;l++){
						if (acf[l] > maxValue ){
							maxIndex_acf = l;
							maxValue = acf[l];
						}
					}

					float freq = fs/(maxIndex_acf);

					pitch[i] = (int) (10*(  (  69+12*(  (float) (  Math.log(freq/440) / Math.log(2.0)  )  )  ) ) );
/*简化暂时删掉*/
//					if ((pitch[i-1] - pitch[i])> 150){
//						int flag_i = 2;
//						pitch[i-1] = pitch[i];
//
//						while (flag_i >= 2) {
//							if ((pitch[i-flag_i] - pitch[i]) >150){
//								pitch[i-flag_i] = 0;
//								flag_i = flag_i +1;
//							}else{
//								flag_i =0;
//							}
//						}
//					}
				}
			}

			ArrayList<Integer> pitch_simplify=new ArrayList<Integer>();
			int L = 2;//基音平滑去野点
			for (int r=0;r<4;r++)
			for (int i = L;i<frameNum-L-1;i++){
				int [] sort=new int[2*L+1];
				for (int j = i-L;j<i+L+1;j++)
					sort[j-i+L]=pitch[j];
				for (int j =0;j<2*L;j++)
					for (int k = j+1;k<2*L+1;k++)
						if (sort[j]>sort[k]){
							sort[j]+=sort[k];
							sort[k]=sort[j]-sort[k];
							sort[j]=sort[j]-sort[k];
						}
				pitch[i-L]=sort[L];
			}
			for(int i = 0;i<frameNum-5*L*2;i++){
				boolean f1,f2;
				f1=f2=true;
				for (int j = i+1;j<i+3;j++) if (pitch[j]!=pitch[i]){
					f1=false;break;
				}
				for (int j = i+1;j<i+5;j++) if (Math.abs(pitch[j]-pitch[i])>=20){
					f2=false;break;
				}
				if (f1&&f2)
					if (pitch_simplify.isEmpty()||pitch[i]!=pitch_simplify.get(pitch_simplify.size()-1))
						if (pitch[i]!=0)
							pitch_simplify.add(pitch[i]);
			}


			sb.delete(0, sb.length());
/*为了简化暂时删掉*/
//			for(int i = 0; i< pitch.length; i ++){
//				sb.append(pitch[i]).append('+');
//			}

			for(int i =0;i<pitch_simplify.size();i++)
			{
				sb.append(pitch_simplify.get(i)+" ");
			}
			//delete the last "+"
			sb.deleteCharAt(sb.length()-1);

		} catch (FileNotFoundException e) {

		} catch (IOException e){

		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private int byte2int(byte b, byte c) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		byte firstByte = b;
		byte secondByte = c;
		bb.put(firstByte);
		bb.put(secondByte);
		return (int)bb.getShort(0);
	}

	private int[][] buffer2(int in_y[],int frame_S, int over_l){
		int step = frame_S-over_l;
		int frameCount = (in_y.length - over_l) / step;
		int[][] out = new int[frameCount][frame_S];
		int startIndex = 0;

		for (int i = 0;i<frameCount;i++){
			for(int j = 0;j<frame_S;j++){
				out[i][j] = 0;
			}
		}
		for (int i=0 ;i< frameCount;i++){
			startIndex = i*step;
			for (int j = 0; j < frame_S;j++){
				out[i][j] = in_y[startIndex+j];
			}
		}
		return out;
	}

	private int dot(int temp_frame[], int start_flag, int range_n1, int range_n2){
		int dot_result = 0;
		if (start_flag >= range_n1 & start_flag < range_n2){
			for (int i = 0; i<temp_frame.length - start_flag;i++){
				dot_result = dot_result + temp_frame[i]*temp_frame[i+start_flag]/50;

			}
		}
		return dot_result;
	}

	private int[] frame2acf(int in_frame[],int max_S,int range_n1,int range_n2,int in_method){
		// trans. from matlab frame2acf, i delete case 3 and 4
		// 1 for using the whole frame for shifting
		// 2 for using the whole frame for shifting, but normalize the sum by it's overlap area
		int frameSize = in_frame.length;
		int[] out = new int[max_S];

		for (int i = 0 ; i< max_S ; i++){
			out[i] = 0;
		}
		switch(in_method){
			case 1 :		// moving base = whole frame
				for (int j=0;j<max_S;j++){
					out[j] = dot(in_frame,j,range_n1,range_n2);
				}
				return out;
			case 2	:	// moving base = whole frame, but normalized by the overlap area
				for (int j=0;j<max_S;j++){
					out[j] = dot(in_frame,j,range_n1,range_n2)/(frameSize-j);	// normalization
				}
				return out;
			default :
				return out;
		}
	}

	private void CopyFromAssets(String sourcefileName, String targetfileName){
		try{
			InputStream in = getAssets().open(sourcefileName);
			OutputStream out = new FileOutputStream(targetfileName);

			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0){
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		} catch(Exception e){

		}
	}

	class RecordThread implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			Message m = new Message();
			m.what=RECORDING;
			myMessageHandler.sendMessage(m);
			recorderInstance = new myWavRecorder();
			Thread th = new Thread(recorderInstance);
			recorderInstance.setwaveFileName(wavFile);
			recorderInstance.setRecording(true);
			th.start();
		}
	}

	class PlayThread implements Runnable {
		String wavFilePath;
		public PlayThread(String path){
			wavFilePath=path;
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				if (!ispaused) {
					try {
						mMediaPlayer.reset();
						mMediaPlayer.setDataSource(wavFilePath);
						mMediaPlayer.prepare();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				mMediaPlayer.start();
				pgb.setMax(mMediaPlayer.getDuration());

				synchronized (this) {
					while (mMediaPlayer.isPlaying()) {
						pgb.setProgress(mMediaPlayer.getCurrentPosition());
						this.wait(10);
					}
//					try {
//						//this.wait(mMediaPlayer.getDuration()*9/10);
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			isplay=false;
			Message m = new Message();
			m.what=PLAY_FINISH;
			myMessageHandler.sendMessage(m);
		}
	}

	class stopThread implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			ispaused=true;
			mMediaPlayer.pause();
		}
	}

	class RecogThread implements Runnable {
		@Override
		public void run() {
			Message m = new Message();
			ispaused=false;
			goRecog();
			m.what=RECOG_FINISH;
			myMessageHandler.sendMessage(m);
		}
	}

	class WordRecThread implements Runnable {
		@Override
		public void run() {
			startSpeechDialog();
		}
	}

	private void startSpeechDialog() {
		mIat = SpeechRecognizer.createRecognizer(this, new MyInitListener());
		// 清空参数
		mIat.setParameter(SpeechConstant.PARAMS, null);
		// 设置返回结果格式
		mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
		mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-2");
		mIat.setParameter(SpeechConstant.ASR_SOURCE_PATH,wavFilePath);
		mIat.setParameter(SpeechConstant. LANGUAGE, "zh_cn" );
		mIat.setParameter(SpeechConstant. ACCENT, "mandarin" );
		mIat.setParameter(SpeechConstant.ASR_PTT, "0");
		mIat.setParameter(SpeechConstant. BACKGROUND_SOUND, String.valueOf(1));
		mIat.setParameter(SpeechConstant. DOMAIN, "music");
		//mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		//mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
		mIat.startListening(recognizerListener);
	}

	class MyInitListener implements InitListener {
		@Override
		public void onInit(int code) {
			if (code != ErrorCode.SUCCESS) {
				Log.i("233","初始化失败 ");
			}
		}
	}

	private RecognizerListener recognizerListener = new RecognizerListener()  {

		@Override
		public void onVolumeChanged(int i, byte[] bytes) {
		}

		@Override
		public void onBeginOfSpeech() {
		}

		@Override
		public void onEndOfSpeech() {

		}

		/**
		 * @param results
		 * @param isLast  是否说完了
		 */
		@Override
		public void onResult(RecognizerResult results, boolean isLast) {

			String result = results.getResultString();
			showTip(result) ;
			System. out.println(" 未解析 :" + result);

			String text = JsonParser.parseIatResult(result) ;//解析过后的
			System. out.println(" 解析后 :" + text);

			String sn = null;
			// 读取json结果中的 sn字段
			try {
				JSONObject resultJson = new JSONObject(results.getResultString()) ;
				sn = resultJson.optString("sn" );
			} catch (JSONException e) {
				e.printStackTrace();
			}

			mIatResults .put(sn, text) ;

			StringBuffer resultBuffer = new StringBuffer();
			for (String key : mIatResults.keySet()) {
				resultBuffer.append(mIatResults .get(key));
			}

			tvword.setText(resultBuffer.toString());// 设置输入框的文本
			Message m = new Message();
			m.what=WORDRECOG_FINISH;
			myMessageHandler.sendMessage(m);
		}

		@Override
		public void onError(SpeechError speechError) {

		}

		@Override
		public void onEvent(int i, int i1, int i2, Bundle bundle) {

		}
	};
	private void showTip (String data) {
		//just for test
	}

}
