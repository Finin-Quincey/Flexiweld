package uob.flexiweld.app;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import uob.flexiweld.Utils;
import uob.flexiweld.app.mode.CaptureMode;
import uob.flexiweld.geom.Line;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
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

	// Camera properties
	private Size cameraResolution;
	private double maxFps;

	private double scaleFactor;
	private Size outputSize;

	/** OpenCV can't be trusted to keep track of this properly so I'm doing it myself! */
	private boolean running;
	/** Keeps track of whether the video feed is paused or not. Pausing the video feed does not release the camera, it
	 * just stops overwriting {@link VideoFeed#raw} on update (and the processing still gets run). */
	private boolean paused;
	/** Keeps track of when the video feed should resume. This is zero if the feed is running or paused indefinitely. */
	private long resumeTime;

	/** Keeps track of the frames per second over the last 10 frames, for a moving average. */
	private final List<Double> recentFps = new ArrayList<>(FPS_AVERAGE_WINDOW);

	public VideoFeed(int cameraNumber){
		this.cameraNumber = cameraNumber;
		vc = new VideoCapture();
	}

	/** Returns the resolution of the camera this video feed has open. */
	public Size getCameraResolution(){
		return cameraResolution;
	}

	/**
	 * Returns the output resolution this video feed is set to.
	 * @see VideoFeed#fit(int, int)
	 */
	public Size getOutputSize(){
		return outputSize;
	}

	/** Returns the current framerate of the camera, capped to the camera's maximum framerate. */
	public double getFps(){
		return Math.min(recentFps.stream().mapToDouble(d -> d).average().orElse(0), maxFps);
	}

	/** Returns true if this video feed is running, false otherwise. */
	public boolean isRunning(){
		return vc.isOpened() && running;
	}

	/** Returns the current output frame, after processing and annotations. */
	public Mat getCurrentFrame(){
		return out;
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
		maxFps = vc.get(Videoio.CAP_PROP_FPS);

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

	/** Pauses the video feed for the given number of milliseconds. */
	public void pauseFor(int milliseconds){
		if(paused) return; // Ignore if it's already paused
		resumeTime = System.currentTimeMillis() + milliseconds;
		paused = true;
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
		scaleFactor = Math.min(width/cameraResolution.width, height/cameraResolution.height);
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

		long time = System.currentTimeMillis();

		if(paused){
			if(resumeTime > 0 && time > resumeTime){
				paused = false;
				resumeTime = 0;
			}
			return HighGui.toBufferedImage(out);
		}

		vc.read(raw);

		// Processing
		out = mode.processFrame(this, raw);

		out = Utils.process(out, (s, d) -> Core.flip(s, d, 1)); // Mirror in x
		out = Utils.process(out, (s, d) -> Imgproc.resize(s, d, outputSize)); // Scale to fit the window

		// Add the annotations afterwards so they don't get scaled
		// This means the positions need to be transformed accordingly, see the methods below
		out = mode.annotateFrame(this, out);

		recentFps.add(1000d / (System.currentTimeMillis() - time));
		if(recentFps.size() > FPS_AVERAGE_WINDOW) recentFps.remove(0);

		return HighGui.toBufferedImage(out);

	}

	/**
	 * Returns a new point with the coordinates of the given point, transformed into the coordinate space of the video
	 * feed output image.
	 * @param point The point (in the raw image space) to be transformed
	 * @return The resulting point, in the output (screen) coordinate space
	 */
	public Point transformForDisplay(Point point){
		return new Point((int)((cameraResolution.width - point.x) * scaleFactor), (int)(point.y * scaleFactor));
	}

	/**
	 * Returns a new line with the coordinates of the given line, transformed into the coordinate space of the video
	 * feed output image.
	 * @param line The line (in the raw image space) to be transformed
	 * @return The resulting line, in the output (screen) coordinate space
	 */
	public Line transformForDisplay(Line line){
		return new Line(transformForDisplay(line.getStart()), transformForDisplay(line.getEnd()));
	}

	/**
	 * Returns a new point with the coordinates of the given point, transformed into the coordinate space of the video
	 * feed output image.
	 * @param points A matrix of points (in the raw image space) to be transformed
	 * @return A matrix of the resulting points, in the output (screen) coordinate space
	 */
	public MatOfPoint2f transformForDisplay(MatOfPoint2f points){
		// Better to stream the array directly than convert to a list first
		return new MatOfPoint2f(Arrays.stream(points.toArray()).map(this::transformForDisplay).toArray(Point[]::new));
	}

}
