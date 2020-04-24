package uob.flexiweld.app;

import org.opencv.core.Mat;

import java.awt.*;
import java.util.List;

public abstract class CaptureMode {

	public final String name;

	public CaptureMode(String name){
		this.name = name;
	}

	public void populateControls(List<Component> components){

	}

	public void populateStatusBar(List<Component> components){

	}

	/**
	 * Processes the given raw video frame and returns the result.
	 * @param videoFeed The video feed object calling this method, for reference.
	 * @param raw The raw video frame, as captured by the camera, with no distortion correction, scaling or other
	 *            modifications. Since some OpenCV methods modify images directly and others require a destination
	 *            matrix, implementors are free to decide whether to modify the raw frame - the {@link Mat} object will
	 *            not be used until the next frame, when it will be overwritten entirely.
	 * @return The resulting frame, after processing
	 */
	public abstract Mat processFrame(VideoFeed videoFeed, Mat raw);

}
