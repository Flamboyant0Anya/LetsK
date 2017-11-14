package org.LetsK.record;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class myWavRecorder implements Runnable {
     private int frequency;
     private int channelConfiguration;
     private volatile boolean isPaused;
     //private File fileName;
     //private File fileNameforplay;
     private File waveFileName;
     private volatile boolean isRecording;
     private final Object mutex = new Object();
     private static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

     public myWavRecorder() {
          super();
          this.setFrequency(16000);
          this.setChannelConfiguration(AudioFormat.CHANNEL_CONFIGURATION_MONO);
          this.setPaused(false);
          this.setRecording(false);
     }

     public void run() {
          // Wait until we're recording...
          synchronized (mutex) {
               while (!this.isRecording) {
                    try {
                         mutex.wait();
                    } catch (InterruptedException e) {
                         throw new IllegalStateException("Wait() interrupted!", e);
                    }
               }
          }
          
          // We're important...
          android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

          // Allocate Recorder and Start Recording...
          ArrayList<Short> allBuf = new ArrayList<Short>(); 
          int bufferSize = AudioRecord.getMinBufferSize(this.getFrequency(),this.getChannelConfiguration(), 
        		  										this.getAudioEncoding());
          AudioRecord recordInstance = new AudioRecord(MediaRecorder.AudioSource.MIC, this.getFrequency(), 
        		  									   this.getChannelConfiguration(), this.getAudioEncoding(),
        		  									   bufferSize);
          //short[] tempBuffer = new short[bufferSize];
          short[] buffer = new short[bufferSize];
          recordInstance.startRecording();
          while (this.isRecording) {
               // Are we paused?
               synchronized (mutex) {
                    if (this.isPaused) {
                         try {
                              mutex.wait(250);
                         } catch (InterruptedException e) {
                              throw new IllegalStateException("Wait() interrupted!",
                                        e);
                         }
                         continue;
                    }
               }
               
               int bufferRead = recordInstance.read(buffer, 0, bufferSize);
               if (bufferRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw new IllegalStateException("read() returned AudioRecord.ERROR_INVALID_OPERATION");
               } else if (bufferRead == AudioRecord.ERROR_BAD_VALUE) {
                    throw new IllegalStateException("read() returned AudioRecord.ERROR_BAD_VALUE");
               } else if (bufferRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw new IllegalStateException("read() returned AudioRecord.ERROR_INVALID_OPERATION");
               }
               for (int idxBuffer = 0; idxBuffer < bufferRead; idxBuffer++) {
                  allBuf.add(buffer[idxBuffer]);
               }
          }
          recordInstance.release();   
          this.save2wav(allBuf);
     }

     public void setwaveFileName(File waveFileName) {
          this.waveFileName = waveFileName;
     }

     public File getwaveFileName() {
          return waveFileName;
     }
     /**
      * @param isRecording
      *            the isRecording to set
      */
     public void setRecording(boolean isRecording) {
          synchronized (mutex) {
               this.isRecording = isRecording;
               if (this.isRecording) {
                    mutex.notify();
               }
          }
     }

     /**
      * @return the isRecording
      */
     public boolean isRecording() {
          synchronized (mutex) {
               return isRecording;
          }
     }

     /**
      * @param frequency
      *            the frequency to set
      */
     public void setFrequency(int frequency) {
          this.frequency = frequency;
     }

     /**
      * @return the frequency
      */
     public int getFrequency() {
          return frequency;
     }

     /**
      * @param channelConfiguration
      *            the channelConfiguration to set
      */
     public void setChannelConfiguration(int channelConfiguration) {
          this.channelConfiguration = channelConfiguration;
     }

     /**
      * @return the channelConfiguration
      */
     public int getChannelConfiguration() {
          return channelConfiguration;
     }

     /**
      * @return the audioEncoding
      */
     public int getAudioEncoding() {
          return audioEncoding;
     }

     /**
      * @param isPaused
      *            the isPaused to set
      */
     public void setPaused(boolean isPaused) {
          synchronized (mutex) {
               this.isPaused = isPaused;
          }
     }

     /**
      * @return the isPaused
      */
     public boolean isPaused() {
          synchronized (mutex) {
               return isPaused;
          }
     }
     
     private void writeInt(DataOutputStream out, int val) throws IOException {
         out.write(val >> 0);
         out.write(val >> 8);
         out.write(val >> 16);
         out.write(val >> 24);
     }

     private void writeShort(DataOutputStream out, short val) throws IOException {
         out.write(val >> 0);
         out.write(val >> 8);
     }

     public void save2wav(ArrayList<Short> allBuf){	 
    	 // open file
    	 if (waveFileName == null) {throw new IllegalStateException("fileName is null");}
    	 if (waveFileName.exists()) {waveFileName.delete();}
    	 
    	 BufferedOutputStream waveBufferedStreamInstance = null;
         try {
        	 waveBufferedStreamInstance = new BufferedOutputStream(new FileOutputStream(waveFileName));
         }
         catch (FileNotFoundException e) {
             throw new IllegalStateException("Cannot Open File", e);
         }
         DataOutputStream waveOutputStreamInstance =new DataOutputStream(waveBufferedStreamInstance);
         
         int bufLen = allBuf.size();
         int mNumBytes = bufLen*2;
         // write wave file
         try {
        	 // write RIFF header
             waveOutputStreamInstance.writeBytes("RIFF");         // ChunkID
             this.writeInt(waveOutputStreamInstance,36+mNumBytes);// ChunkSize
             waveOutputStreamInstance.writeBytes("WAVE");         // Format
             
             // write fmt chunk
             int ch = 1;
             waveOutputStreamInstance.writeBytes("fmt ");         // Subchunk1ID
             this.writeInt(waveOutputStreamInstance,16);          // Subchunk1Size(16 for PCM)
             this.writeShort(waveOutputStreamInstance,(short)1);  // AudioFormat(PCM = 1)
             this.writeShort(waveOutputStreamInstance,(short)ch);   // NumChannels
             this.writeInt(waveOutputStreamInstance,frequency);   // SampleRate
             this.writeInt(waveOutputStreamInstance,ch*frequency*2); // ByteRate  (fs*ch*nbits/8)
             this.writeShort(waveOutputStreamInstance,(short)(ch*2));// BlockAlign(ch*fs/8)
             this.writeShort(waveOutputStreamInstance,(short)16); // BitsPerSample(fix to 16)
             
             // write data chunk
             waveOutputStreamInstance.writeBytes("data");         // Subchunk2ID
             this.writeInt(waveOutputStreamInstance,mNumBytes);   // Subchunk2Size
             //write sample points
        	 for(int i=0;i<bufLen;i++){
        		 this.writeShort(waveOutputStreamInstance,allBuf.get(i));
        	 }
        	 
        	 waveOutputStreamInstance.flush();
        	 waveOutputStreamInstance.close();
         }
         catch (IOException e) {
        	 throw new IllegalStateException("Wave write error ...QAQ");
         }
     }
} 