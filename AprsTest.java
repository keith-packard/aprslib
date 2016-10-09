/*
 * Copyright © 2016 Keith Packard <keithp@keithp.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

/*
 * This code uses ideas and algorithms from Dire Wold, an amateur
 * radio packet TNC which was written by John Langner, WB2OSZ. That
 * project is also licensed under the GPL, either version 2 of the
 * License or (at your option) any later version.
 */

package org.altusmetrum.aprslib_1;

import java.io.*;
import	javax.sound.sampled.*;

class packet implements AprsPacket {
	public void receive(AprsAprs packet) {
		System.out.printf("%s\n", packet.toString());
	}

	public void carrier_detect(boolean detect) {
	}
}

public class AprsTest {

	public AprsTest() {
	}

	static private int sample(FileInputStream f) throws IOException {
		int	a = f.read();
		if (a == -1)
			return -1;
		int	b = f.read();
		if (b == -1)
			return -1;

		return a | (b << 8);
	}

	static void dump_filter(AprsFilter f) {
		float[]	c = f.coeff;

		for (int i = 0; i < c.length; i++)
			System.out.printf ("%d %g\n", i, c[i]);
	}

	static void shutdown(String reason) {
		System.err.printf("failed: %s\n", reason);
		System.exit(1);
	}

	class Capture {
		AudioInputStream	input_stream;
		AudioFormat 		format;
		AprsDemod		demod;

		public void process(byte[] data, int length) {
			for (int i = 0; i < length; i += 2) {
				int	a = data[i] & 0xff;
				int	b = data[i+1] & 0xff;
				short	input = (short) (a | (b << 8));
				float raw = input / 16384.0f;
				demod.demod(raw);
			}
		}

		public void capture () {

			AudioFormat.Encoding	encoding = AudioFormat.Encoding.PCM_SIGNED;
			float			sample_rate = 44100.0f;
			int			sample_size = 16;
			int			channels = 1;
			int			frame_size = sample_size / 8 * channels;
			boolean			big_endian = false;

			format = new AudioFormat(encoding,
						 sample_rate,
						 sample_size,
						 channels,
						 frame_size,
						 sample_rate,
						 big_endian);

			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

			if (!AudioSystem.isLineSupported(info)) {
				System.out.printf("can't get desired format\n");
				System.exit(1);
			}

			TargetDataLine	line = null;

			try {
				line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format, line.getBufferSize());
			} catch (LineUnavailableException ex) {
				shutdown("Line unavailable\n");
			} catch (SecurityException ex) { 
				shutdown(ex.toString());
			} catch (Exception ex) { 
				shutdown(ex.toString());
			}

			int frameSizeInBytes = format.getFrameSize();
			int bufferLengthInFrames = line.getBufferSize() / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] data = new byte[bufferLengthInBytes];
			int numBytesRead;

			System.out.printf("frame bytes %d buffer frames %d buffer bytes %d\n",
					  frameSizeInBytes, bufferLengthInFrames, bufferLengthInBytes);

			line.start();

			for (;;) {
				if((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
					break;
				}
				process(data, numBytesRead);
			}
		}

		public Capture (AprsDemod demod) {
			this.demod = demod;
		}
	}

	public void run(final String[] args) {
		AprsDemod	demod = new AprsDemod(new packet(), 44100);
		boolean		any_read = false;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("capture")) {
				Capture c = new Capture(demod);

				c.capture();
			} else {
				try {
					FileInputStream f = new FileInputStream(args[i]);
					try {
						for (;;) {
							int input = sample(f);
							if (input == -1)
								break;
							if (input >= 32768)
								input = input - 65536;
							float raw = input / 16384.0f;
							demod.demod(raw);
						}
						any_read = true;
					} catch (IOException ie) {
					}
					try {
						f.close();
					} catch (IOException ie) {
					}
				} catch (FileNotFoundException fe) {
					System.out.printf("No such file %s\n", args[i]);
				}
			}
		}
		if (any_read)
			demod.flush();
	}

	public static void main(final String[] args) {
		AprsTest	t = new AprsTest();

		t.run(args);
	}
}
