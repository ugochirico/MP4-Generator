# MP4-Generator
A simple class to generate MP4 video by mixing images, videos and audio tracks

'''java

File[] imageFiles = new File("images").listFiles();
ArrayList<Frame> frameArray = new ArrayList<>();
int duration = 20;		

for(File f : imageFiles)
{
	System.out.println("image " + f.getName());

	try 
	{
		BufferedImage baseImage = ImageIO.read(f);

		int w = baseImage.getWidth();
		int h = baseImage.getHeight();

		for(int i = 0; i < 8; i++)
		{
			BufferedImage image = baseImage;

			int x = 0;
			int y = 0;
			int w0 = w;
			int h0 = h;

			// crop if needed
			if(w > W)
			{
				int dx = (w - W);
				int offset = (int)(Math.random() * (double)dx); 
				x = x + offset;
				w0 = W;
				System.out.println("dx: " + dx + " offset: " + offset + " x: " + x + " w0: " + w0 + " w: " + w);

			}
			'''

			if(h > H)
			{
				int dy = (h - H);
				int offset = (int)(Math.random() * (double)dy); 
				y = y + offset;
				h0 = H;

				System.out.println("dy: " + dy + " offset: " + offset + " y: " + y + " h0: " + h0 + " h: " + h);
			}

			if(x != 0 || y != 0)
			{
				image = image.getSubimage(x, y, w0, h0);						
			}


			Image image1 =  image.getScaledInstance(W, H, Image.SCALE_SMOOTH);

			ImageFrame imageFrame = new ImageFrame();
			imageFrame.bitmap = toBufferedImage(image1);
			imageFrame.durationmSec = 400;

			frameArray.add(imageFrame);					
		}
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
}

MP4GeneratorJCodec mp4gen = new MP4GeneratorJCodec();


try {
	mp4gen.init("photo", "16/9", "audio.aac", frameArray, "myvideo.mp4");

	mp4gen.generate();

} catch (IOException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
}
    
