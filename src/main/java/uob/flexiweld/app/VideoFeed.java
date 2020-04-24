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
// N.B. If we're getting technical, this is the *context* part of a *state pattern*.
public class VideoFeed {

	private final int cameraNumber;

	private VideoCapture vc;
	private Mat raw;
	private Mat out;

	private Size cameraResolution;

	private Size outputSize;

	/** OpenCV can't be trusted to keep track of this properly so I'm doing it myself! */
	private boolean running;

	private float fps;

	private CaptureMode mode;

	public VideoFeed(int cameraNumber){
		this.cameraNumber = cameraNumber;
		vc = new VideoCapture();
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

		mode = new CalibrationMode(new Size(9, 6), 25);

		running = true;
		return true;
	}

	/** Closes this video feed's camera, releasing it for other uses. */
	public void stop(){
		// TODO: There's an issue with this version of OpenCV, update to a newer one! https://github.com/opencv/opencv/issues/12301
		vc.release();
		running = false;
	}

	/** Returns true if this video feed is running, false otherwise. */
	public boolean isRunning(){
		return vc.isOpened() && running;
	}

	/**
	 * Adjusts the output resolution of this video feed to fit the given dimensions.
	 * @param width The maximum width to fit the output to
	 * @param height The maximum height to fit the output to
	 */
	public void fit(int width, int height){
		double scaleFactor = Math.min(width/cameraResolution.width, height/cameraResolution.height);
		int newWidth = (int)(cameraResolution.width * scaleFactor);
		int newHeight = (int)(cameraResolution.height * scaleFactor);
		outputSize = new Size(newWidth, newHeight);
	}

	/**
	 * Returns the output resolution this video feed is set to.
	 * @see VideoFeed#fit(int, int)
	 */
	public Size getOutputSize(){
		return outputSize;
	}

	/** Returns the current framerate of the camera. */
	public float getFps(){
		return fps;
	}

	/**
	 * Reads the next frame of this video feed, processes it according to the current mode, and returns the resulting
	 * output image as an {@link Image} object, ready for rendering into a Swing UI or similar.
	 * @return An {@link Image} containing the processed frame, scaled to fit the output resolution, and with any
	 * annotations added.
	 * @throws IllegalStateException if the camera is not currently opened
	 */
	public Image update(){

		long time = System.currentTimeMillis();

		if(!isRunning()) throw new IllegalStateException("Video feed not running!");

		vc.read(raw);

		// Processing
		out = mode.processFrame(this, raw);

		out = Utils.process(out, (s, d) -> Core.flip(s, d, 1));
		out = Utils.process(out, (s, d) -> Imgproc.resize(s, d, outputSize));

		fps = 1000f / (System.currentTimeMillis() - time);

		return HighGui.toBufferedImage(out);

	}

}
