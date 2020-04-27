package uob.flexiweld.app;

import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import uob.flexiweld.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The main class that deals with the actual video feed. This class forms the boundary between the high-level Swing UI
 * class (see {@link FlexiweldApp}) and the lower-level classes that interact with OpenCV. Effectively, this means
 * everything inside the video frame.
 * <p></p>
 * The application maintains a single {@code VideoFeed} object which is responsible for reading frames from the camera
 * and delegating processing of that frame accordingly.
 *
 * @author Finin Quincey
 */
public class VideoFeed {

	private static final int FPS_AVERAGE_WINDOW = 10;

	private final int cameraNumber;

	private VideoCapture vc;
	private Mat raw;
	private Mat out;

	private Size cameraResolution;

	private Size outputSize;

	/** OpenCV can't be trusted to keep track of this properly so I'm doing it myself! */
	private boolean running;
	/** Keeps track of whether the video feed is paused or not. Pausing the video feed does not release the camera, it
	 * just stops overwriting {@link VideoFeed#raw} on update (and the processing still gets run). */
	private boolean paused;

	/** Keeps track of the frames per second over the last 10 frames, for a moving average. */
	private final List<Double> recentFps = new ArrayList<>(FPS_AVERAGE_WINDOW);

	public VideoFeed(int cameraNumber){
		this.cameraNumber = cameraNumber;
		vc = new VideoCapture();
	}

	/**
	 * Returns the output resolution this video feed is set to.
	 * @see VideoFeed#fit(int, int)
	 */
	public Size getOutputSize(){
		return outputSize;
	}

	/** Returns the current framerate of the camera. */
	public double getFps(){
		return recentFps.stream().mapToDouble(d -> d).average().orElse(0);
	}

	/** Returns true if this video feed is running, false otherwise. */
	public boolean isRunning(){
		return vc.isOpened() && running;
	}

	/**
	 * Opens this video feed's camera, reads a single frame and records its resolution for internal use.
	 * @return True if the video capture was opened successfully, false if not (i.e. if the camera is in use or
	 * otherwise inaccessible)
	 */
	public boolean start(){

		raw = new Mat();
		out = new Mat();

		try{
			if(!vc.open(cameraNumber)) return false;
		}catch(CvException e){
			// Sometimes throws an exception if the camera is in use or otherwise inaccessible it seems
			System.out.println("Failed to open video capture");
			return false;
		}

		if(!vc.read(raw)) return false; // For some strange reason open can succeed when the camera is busy...
		cameraResolution = raw.size();

		running = true;
		return true;
	}

	/** Closes this video feed's camera, releasing it for other uses. */
	public void stop(){
		// TODO: There's an issue with this version of OpenCV, update to a newer one! https://github.com/opencv/opencv/issues/12301
		vc.release();
		running = false;
	}

	/** Toggles whether the video feed is paused. Pausing the video feed does not release the camera, it just freezes
	 * the video so new frames are not captured (the rest of the frame processing still gets run, using the last frame
	 * that was captured). */
	public void togglePause(){
		paused = !paused;
	}

	/** Returns whether the video feed is currently paused. */
	public boolean isPaused(){
		return paused;
	}

	/**
	 * Adjusts the output resolution of this video feed to fit the given dimensions.
	 * @param width The maximum width to fit the output to
	 * @param height The maximum height to fit the output to
	 * @throws IllegalStateException if the camera is not currently opened
	 */
	public void fit(int width, int height){
		if(!isRunning()) throw new IllegalStateException("Video feed not running!");
		double scaleFactor = Math.min(width/cameraResolution.width, height/cameraResolution.height);
		int newWidth = (int)(cameraResolution.width * scaleFactor);
		int newHeight = (int)(cameraResolution.height * scaleFactor);
		outputSize = new Size(newWidth, newHeight);
	}

	/**
	 * Reads the next frame of this video feed, processes it according to the current mode, and returns the resulting
	 * output image as an {@link Image} object, ready for rendering into a Swing UI or similar.
	 * @return An {@link Image} containing the processed frame, scaled to fit the output resolution, and with any
	 * annotations added.
	 * @throws IllegalStateException if the camera is not currently opened
	 */
	public Image update(CaptureMode mode){

		if(!isRunning()) throw new IllegalStateException("Video feed not running!");

		if(paused) return HighGui.toBufferedImage(out);

		long time = System.currentTimeMillis();

		vc.read(raw);

		// Processing
		out = mode.processFrame(this, raw);

		out = Utils.process(out, (s, d) -> Core.flip(s, d, 1));
		out = Utils.process(out, (s, d) -> Imgproc.resize(s, d, outputSize));

		recentFps.add(1000d / (System.currentTimeMillis() - time));
		if(recentFps.size() > FPS_AVERAGE_WINDOW) recentFps.remove(0);

		return HighGui.toBufferedImage(out);

	}

}
