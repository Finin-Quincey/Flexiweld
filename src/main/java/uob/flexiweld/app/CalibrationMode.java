package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.core.Size;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CalibrationMode extends CheckerboardDetectionMode {

	JLabel imageCountReadout;

	public CalibrationMode(Size checkerboardSize, double squareSize){
		super("Calibration", checkerboardSize, squareSize);
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		super.populateControls(app, components);
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		imageCountReadout = FlexiweldApp.addStatusText("Capturing calibration images (0/10)", components);
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		return super.processFrame(videoFeed, raw);
	}

}
