package org.LetsK.letsk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.LetsK.record.myWavRecorder;

public class LetsK extends Activity {

	final StringBuffer sb = new StringBuffer();
	String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();//????
	String projectPath = sdDir + "/LetsK";
	String loadUrlPic_portrait = "android-robot-icon3.jpg";
	String loadUrlPic_landscape = "android-robot-icon2.jpg";
	String webLoadPic_portrait = "loading.jpg";
	String webLoadPic_landscape = "loading_landscape.jpg";
	String wavFilePath = projectPath + "/recorder.wav";
	File wavFile;

	TextView tv,tvpitch,tvword;
	Button recordBtn, playBtn;
	//int recordDuration = 8;//????
	myWavRecorder recorderInstance;
	Thread recordThread, playThread, recogThread, stopThread;
	MediaPlayer mMediaPlayer;
	boolean isplay,ispaused,isrecording;

	protected static final int RECORDING = 101;
	protected static final int REC_FINISH = 102;
	protected static final int RECOG = 103;
	protected static final int RECOG_FINISH = 104;
	protected static final int PLAY = 105;
	protected static final int PLAY_FINISH = 106;
	protected static final int STOP = 107;

	OnClickListener btnListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			if (v.getId() == R.id.record) {
				if (!isrecording) {
					//recordBtn.setEnabled(false);
					isrecording=true;
					playBtn.setEnabled(false);
					recordThread = new Thread(new RecordThread());
					recordThread.start();
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
						playThread = new Thread(new PlayThread());
						playThread.start();
					}
					else {
						Message m = new Message();
						m.what = STOP;
						myMessageHandler.sendMessage(m);
						recordBtn.setEnabled(true);
						playBtn.setEnabled(true);
						isplay=false;
						playThread.interrupt();
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
        recordBtn = (Button) findViewById(R.id.record);
        playBtn = (Button) findViewById(R.id.play);
		mMediaPlayer = new MediaPlayer();

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
					playThread.interrupt();
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
			}
			super.handleMessage(msg);
		}
	};

	private void goRecog() {
    	try{
    		Thread.sleep(3000);
			while(!wavFile.exists()) {wavFile = new File(wavFilePath);}
    		FileInputStream fileIS = new FileInputStream(wavFile);

    		int wav_len = (int) wavFile.length();
    		// 44Byte header of WAV file, which is 22 short integer long
    		int offset = 22;
    		// frame number
    		int frame_num = (int)((wav_len / 2 - offset) / 512);
    		byte[] buffer = new byte[wav_len];
    		fileIS.read(buffer, 0, wav_len);

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

    				pitch[i] = 10*(  (int) (  69+12*(  (float) (  Math.log(freq/440) / Math.log(2.0)  )  )  )  );

    				if ((pitch[i-1] - pitch[i])> 150){
    					int flag_i = 2;
    					pitch[i-1] = pitch[i];

    					while (flag_i >= 2) {
    						if ((pitch[i-flag_i] - pitch[i]) >150){
    							pitch[i-flag_i] = 0;
    							flag_i = flag_i +1;
    						}else{
    							flag_i =0;
    						}
    					}
    				}
    			}
    		}
    		sb.delete(0, sb.length());

    		for(int i = 0; i< pitch.length; i ++){
    			sb.append(pitch[i]).append('+');
    		}
    		//delete the last "+"
    		sb.deleteCharAt(sb.length()-1);

    	} catch (FileNotFoundException e) {

		} catch (IOException e){

		} catch (InterruptedException e) {
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
                synchronized (this) {
                    try {
                    	this.wait(mMediaPlayer.getDuration()*9/10);
                    } catch (InterruptedException e) {
                    	e.printStackTrace();
                    }
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
			ispaused=false;
			goRecog();
			Message m = new Message();
			m.what=RECOG_FINISH;
			myMessageHandler.sendMessage(m);
		}
    }
}