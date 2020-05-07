package uob.flexiweld.app.mode;

import org.opencv.core.Mat;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;

import java.awt.*;
import java.util.List;

/**
 * Capture mode for when the video feed is not running. This simply displays the start button, and does nothing else.
 * @author Finin Quincey
 */
public class StandbyMode extends CaptureMode {

	public StandbyMode(){
		super("Standby");
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		components.add(FlexiweldApp.createButton("\u25b6 Start", e -> app.toggleCamera()));
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){
		return frame; // Do nothing (will never be called anyway)
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){
		return frame; // Do nothing (will never be called anyway)
	}
}
