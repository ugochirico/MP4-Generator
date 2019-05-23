package com.ugochirico.mp4gen;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;
import org.jcodec.common.model.Size;
import org.mp4parser.Container;
import org.mp4parser.muxer.FileDataSourceImpl;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Mp4TrackImpl;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.TrackMetaData;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.AACTrackImpl;
import org.mp4parser.muxer.tracks.AppendTrack;
import org.mp4parser.muxer.tracks.ClippedTrack;

/**
 * Created by UgoChirico on 24/04/2018.
 * http://www.ugochirico.com
 */

public class MP4GeneratorJCodec
{
    public static class Frame
    {
        long durationmSec;        
        File frameFile;
    }

    public static class ImageFrame extends Frame
    {
    	BufferedImage bitmap;
    }

    public static class VideoFrame extends Frame
    {
    	Movie video;
    	int videoIndex;
    }

    private static final int FRAME_RATE = 25;
    public static final Size VIDEO_SIZE_LANDSCAPE = new Size(640,360);
    public static final Size VIDEO_SIZE_PORTRAIT = new Size(360,640);

    private ArrayList<Frame> frameArrayList;

    private String videoPath;
    private Track audioTrack;
    private File tempVideoFile;
    private long videoDuration;
    private String type;

    private TrackMetaData defaultTrackMetadata;
    private ArrayList<File> tempMovieFileArray;
    
    public void init(String type, String aspectRatio, String audioFile, ArrayList<Frame> frameList, String videoPath) throws IOException
    {        
        this.videoPath = videoPath;
        this.tempVideoFile = new File("pre-"+ videoPath);;//File.createTempFile("video", ".mp4");        
        this.frameArrayList = frameList;
        this.type = type;
        
        tempMovieFileArray = new ArrayList<>();
    	
    	if(type.equals("photo"))
    	{
    		if(audioFile != null)
    		{
    			this.audioTrack = new AACTrackImpl(new FileDataSourceImpl(audioFile));
    		}
    	}    	    
    }

    public void generate()
    {
    	if(type.equals("photo"))
    	{
    		encodePhoto();
    	}
    	else
    	{
    		encodeVideo();
    	}
    }

    private void encodePhoto()
    {
        SeekableByteChannel out = null;
        AWTSequenceEncoder encoder = null;
        
        try
        {
            long t0 = System.currentTimeMillis();
           
            System.out.println("Start Mp4 encoding");
            
            out = NIOUtils.writableFileChannel(tempVideoFile.getAbsolutePath());
                	  
    	    encoder = new AWTSequenceEncoder(out, Rational.R(25, 1));

            ImageFrame frame;
            long frameDurationInFrame;            
            for (int frameIndex = 0; frameIndex < frameArrayList.size(); frameIndex++)
            {
//            	System.out.println("frameIndex " + frameIndex);
            	
                frame = (ImageFrame)frameArrayList.get(frameIndex);

                frameDurationInFrame = Math.round(((double) frame.durationmSec / 1000.0) * (double) FRAME_RATE);

                videoDuration += frame.durationmSec;
                
                BufferedImage image = frame.bitmap;
    	        
                for (int i = 0; i < frameDurationInFrame; i++)
                {
//                	System.out.println("frame " + i);
        	        encoder.encodeImage(image);
                }
            }

            encoder.finish();
            long t1 = System.currentTimeMillis();
            System.out.println("Finish Mp4 encoding " + (t1 - t0));

        }
        catch (Exception e)
        {
        	e.printStackTrace();
        }
        finally
        {
        	if(out != null)
        		NIOUtils.closeQuietly(out);
        }
        
        
        try
        {
          Movie movie = MovieCreator.build(tempVideoFile.getAbsolutePath());

          if(audioTrack != null)
          {
	          //AACTrackImpl aacTrack = new AACTrackImpl(new FileDataSourceImpl(audioFile));
	          
	          long endSample = getSampleForTime(audioTrack, videoDuration);
	          ClippedTrack croppedTrack = new ClippedTrack(audioTrack, 0, endSample);
	          
	          movie.addTrack(audioTrack);
          }
          
          Container mp4file = new DefaultMp4Builder().build(movie);

          FileOutputStream fins = new FileOutputStream(videoPath); 
          FileChannel fc = fins.getChannel();
          mp4file.writeContainer(fc);
          fc.close();
          fins.close();
      }
      catch(IOException ex)
      {
          ex.printStackTrace();
      }

       tempVideoFile.delete();  
    }
        
    private void encodeVideo()
    {
    	 try
         {
           Movie movie = new Movie();//MovieCreator.build(tempVideoFile.getAbsolutePath());

           if(audioTrack != null)
           {
        	   long endSample = getSampleForTime(audioTrack, videoDuration);
        	   ClippedTrack croppedTrack = new ClippedTrack(audioTrack, 0, endSample);
	          
        	   movie.addTrack(croppedTrack);
           }
           
           long t0 = System.currentTimeMillis();
           
           System.out.println("Start Mp4 encoding");
           
          
           long currentTime = 0;
           
           VideoFrame frame;
           
           ArrayList<Track> tracksToAppend = new ArrayList<>();
           
           for (int frameIndex = 0; frameIndex < frameArrayList.size(); frameIndex++)
           {
               frame = (VideoFrame)frameArrayList.get(frameIndex);

               Movie video = frame.video;
   	        
               List<Track> trackList = video.getTracks();
               
               Mp4TrackImpl videoTrack;
               
               for(Track track : trackList)
               {
            	   if(track instanceof Mp4TrackImpl && track.getHandler().startsWith("vide"))
            	   {
            		   videoTrack = (Mp4TrackImpl)track;
            		   
            		   long startSample = getSampleForTime(videoTrack, currentTime);            		   
            		   long endSample = getSampleForTime(videoTrack, currentTime + frame.durationmSec);
                	   ClippedTrack croppedTrack = new ClippedTrack(videoTrack, startSample, endSample);
        	           	   
                	   tracksToAppend.add(croppedTrack);                	      
                	   
                	   currentTime += frame.durationmSec;
                	   
            		   break;
            	   }            	               	  
               }                                                     
           }

           AppendTrack appendTrack = new AppendTrack(tracksToAppend.toArray(new Track[tracksToAppend.size()]));
           
           appendTrack.getTrackMetaData().setHeight(defaultTrackMetadata.getHeight());
           appendTrack.getTrackMetaData().setWidth(defaultTrackMetadata.getWidth());
           appendTrack.getTrackMetaData().setTimescale(defaultTrackMetadata.getTimescale());
                      
           movie.addTrack(appendTrack);
           
           Container mp4file = new DefaultMp4Builder().build(movie);
           
           FileOutputStream fins = new FileOutputStream(videoPath); 
           FileChannel fc = fins.getChannel();
           mp4file.writeContainer(fc);
           fc.close();
           fins.close();
       }
       catch(IOException ex)
       {
           ex.printStackTrace();
       }

        tempVideoFile.delete();  
        
        if(tempMovieFileArray != null)
        {
        	for(File f : tempMovieFileArray)
        	{
        		f.delete();
        	}
        }
    }
    
    public long getSampleForTime(Track track, long durationInMillis)
    {
    	double startTime1 = 0;//correctTimeToSyncSample(track, 0, false);
    	double endTime1 = (double)durationInMillis / 1000.0;//correctTimeToSyncSample(track, duration, true);
    	 
	    long currentSample = 0;
	    double currentTime = 0;
	    double lastTime = -1;
	    long endSample1 = -1;
	    
		for (int i = 0; i < track.getSampleDurations().length; i++) {
		    long delta = track.getSampleDurations()[i];

	        if (currentTime > lastTime && currentTime <= startTime1) {
	        }
	        
	        if (currentTime > lastTime && currentTime <= endTime1) {
	            // current sample is after the new start time and still before the new endtime
	            endSample1 = currentSample;
	        }
	        	        
	        lastTime = currentTime;
	        currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
	        currentSample++;
		}
		
		return endSample1;
    }
    
//    private Bitmap addWatermark(String pathfilename, Bitmap waterMark) {
//
//        Bitmap src = BitmapFactory.decodeFile(pathfilename);
//        int w = src.getWidth();
//
//        int h = src.getHeight();
//        Point location = new Point(w - waterMark.getWidth() - 10,h - waterMark.getHeight() - 10);
//
//        Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());
//        Canvas canvas = new Canvas(result);
//        canvas.drawBitmap(src, 0, 0, null);
//        canvas.drawBitmap(waterMark, location.x, location.y, null);
//
//        return result;
//
//    }


}
