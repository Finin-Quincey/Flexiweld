package uob.flexiweld;

import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SliderGui implements ActionListener {

	public static final int FRAMERATE = 50;
	public static final Scalar ANNOTATION_COLOUR = new Scalar(0, 255, 0); // This is blue-green-red for some reason

	private final VideoCapture vc;
	public final Size size;
	private final Mat mapX, mapY;

	private int threshold1 = 50;
	private int threshold2 = 200;
	private int aperture = 3;

	private int houghThreshold = 50;
	private int minLineLength = 50;
	private int maxLineGap = 10;

	private Mat frame;
	private Mat edges;
	private JFrame jFrame;
	private Timer timer;
	private JLabel imgLabel;

	public SliderGui(int cameraNumber, int width, int height){

		vc = new VideoCapture();
		vc.open(cameraNumber);
		System.out.println(vc.isOpened() ? "Video capture opened successfully" : "Failed to open video capture");

		frame = new Mat();
		vc.read(frame);

		// Create and set up the window.
		jFrame = new JFrame("Calibration");
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		float scaleFactor = Math.min(width/ frame.width(), height/ frame.height());
		int newWidth = (int)(frame.width() * scaleFactor);
		int newHeight = (int)(frame.height() * scaleFactor);
		size = new Size(newWidth, newHeight);

		mapX = new Mat(size, CvType.CV_32F);
		mapY = new Mat(size, CvType.CV_32F);
		Utils.setupFlipMatrices(mapX, mapY);

		// Set up the content pane.
		Image img = HighGui.toBufferedImage(frame);
		addComponentsToPane(jFrame.getContentPane(), img);

		// Use the content pane's default BorderLayout. No need for
		// setLayout(new BorderLayout());
		// Display the window.
		jFrame.pack();
		jFrame.setSize((int)size.width, (int)size.height);
		jFrame.setVisible(true);

		timer = new Timer(1000/FRAMERATE, this);
		timer.setInitialDelay(50);
		timer.start();

	}

	private void addComponentsToPane(Container pane, Image img){

		if(!(pane.getLayout() instanceof BorderLayout)){
			pane.add(new JLabel("Container doesn't use BorderLayout!"));
			return;
		}

		JPanel sliderPanel = new JPanel();
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.PAGE_AXIS));

		sliderPanel.add(new JLabel("Canny Edge Detector Parameters"));

		JSlider slider;

		sliderPanel.add(new JLabel("Threshold 1"));
		slider = createSlider(0, 2000, threshold1, 20, 80);
		slider.addChangeListener(e -> threshold1 = ((JSlider)e.getSource()).getValue());
		sliderPanel.add(slider);

		sliderPanel.add(new JLabel("Threshold 2"));
		slider = createSlider(0, 2000, threshold2, 20, 80);
		slider.addChangeListener(e -> threshold2 = ((JSlider)e.getSource()).getValue());
		sliderPanel.add(slider);

		sliderPanel.add(new JLabel("Aperture"));
		slider = createSlider(3, 7, aperture, 2, 2);
		slider.addChangeListener(e -> aperture = 1 + 2 * Math.round((((JSlider)e.getSource()).getValue() - 1) / 2f));
		sliderPanel.add(slider);

		sliderPanel.add(new JLabel("Hough Line Transform Parameters"));

		sliderPanel.add(new JLabel("Threshold"));
		slider = createSlider(0, 2000, houghThreshold, 20, 80);
		slider.addChangeListener(e -> houghThreshold = ((JSlider)e.getSource()).getValue());
		sliderPanel.add(slider);

		sliderPanel.add(new JLabel("Minimum Line Length"));
		slider = createSlider(0, 1000, minLineLength, 20, 80);
		slider.addChangeListener(e -> minLineLength = ((JSlider)e.getSource()).getValue());
		sliderPanel.add(slider);

		sliderPanel.add(new JLabel("Maximum Line Gap"));
		slider = createSlider(0, 200, maxLineGap, 20, 80);
		slider.addChangeListener(e -> maxLineGap = ((JSlider)e.getSource()).getValue());
		sliderPanel.add(slider);

		pane.add(sliderPanel, BorderLayout.PAGE_START);
		imgLabel = new JLabel(new ImageIcon(img));
		pane.add(imgLabel, BorderLayout.CENTER);

	}

	private JSlider createSlider(int min, int max, int value, int majorTicks, int minorTicks){
		JSlider slider = new JSlider(min, max, value);
		slider.setMajorTickSpacing((max - min) / majorTicks);
		slider.setMinorTickSpacing((max - min) / minorTicks);
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		return slider;
	}

	@Override
	public void actionPerformed(ActionEvent e){

		vc.read(frame);

		frame = Utils.process(frame, (s, d) -> Imgproc.resize(s, d, size));
		frame = Utils.process(frame, (s, d) -> Imgproc.remap(s, d, mapX, mapY, Imgproc.INTER_LINEAR));

		// Edge detection
		edges = Utils.process(frame, (s, d) -> Imgproc.Canny(s, d, threshold1, threshold2, aperture, false));

		// Colour profile conversion
		frame = Utils.process(edges, (s, d) -> Imgproc.cvtColor(s, d, Imgproc.COLOR_GRAY2BGR));

		// Probabilistic Hough Line Transform
		Mat lines = new Mat(); // will hold the results of the detection
		Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, houghThreshold, minLineLength, maxLineGap); // runs the actual detection
		// Draw the lines
		for(int x = 0; x < lines.rows(); x++){
			double[] l = lines.get(x, 0);
			Imgproc.line(frame, new org.opencv.core.Point(l[0], l[1]), new Point(l[2], l[3]), ANNOTATION_COLOUR, 2, Imgproc.LINE_AA, 0);
		}

		Image img = HighGui.toBufferedImage(frame);
		imgLabel.setIcon(new ImageIcon(img));

		jFrame.repaint();

	}

	public static void main(String[] args){
		// Load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		// Schedule a job for the event dispatch thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(() -> new SliderGui(0, 1920, 1080));
	}
}