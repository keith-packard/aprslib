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

public class AprsFilter {

	float[]	coeff;
	int filter;
	int window;

	/* center of the filter (in samples) */
	float center;

	/* cycles per sample, which is frequency / samples per second */
	float cps;
	float cps_low;
	float cps_high;

	static public final int	filter_cos = 1;
	static public final int	filter_sin = 2;
	static public final int filter_lowpass = 3;
	static public final int filter_bandpass = 4;
	static public final int filter_highpass = 5;

	static private final float pi = (float) Math.PI;

	private float sinf(float f) {
		return (float) Math.sin(f);
	}

	private float cosf(float f) {
		return (float) Math.cos(f);
	}

	public float convolve(AprsRing ring) {
		float	sum = 0.0f;

		for (int i = 0; i < coeff.length; i++)
			sum += ring.get(i) * coeff[i];
		return sum;
	}

	private float coeff(int i) throws IllegalArgumentException {
		float	offset = i - center;

		switch (filter) {
		case filter_cos:
			return cosf(offset * cps * 2 * pi);
		case filter_sin:
			return sinf(offset * cps * 2 * pi);
		case filter_lowpass:
			if (offset == 0.0f)
				return 2 * cps;
			return sinf(2.0f * pi * cps * offset) / (pi * offset);
		case filter_bandpass:
			if (offset == 0.0f)
				return 2.0f * (cps_high - cps_low);
			else
				return sinf(2 * pi * cps_high * offset) / (pi * offset) -
					sinf(2 * pi * cps_low * offset) / (pi * offset);
		default:
			throw new IllegalArgumentException(String.format("Unknown filter %d", filter));
		}
	}

	private float normalize(int i, float coeff, float shape) {
		switch (filter) {
		case filter_cos:
		case filter_sin:
			/* unity gain at target freq */
			return coeff * coeff * shape;
		case filter_lowpass:
			/* unity gain at DC */
			return coeff * shape;
		case filter_bandpass:
			/* unity gain in middle of passband. */
			return 2 * coeff * shape * AprsDsp.cos(2 * pi * cps * (i - center));
		default:
			return 1.0f;
		}
	}

	/* Construct the filter by combining the impulse response values and the window.
	 * Then normalize to unity gain at the appropriate frequency
	 */
	private void build(int filter, int window, int size) throws IllegalArgumentException {
		this.filter = filter;
		this.window = window;
		this.center = 0.5f * (size - 1);
		this.coeff = new float[size];

		float norm = 0.0f;
		for (int i = 0; i < size; i++) {
			float c = coeff(i);
			float s = AprsDsp.window(window, size, i);
			float n = normalize(i, c, s);
			coeff[i] = c * s;
			norm += n;
		}

		/* normalize to adjust gain */
		for (int i = 0; i < size; i++)
			coeff[i] /= norm;
	}

	public AprsFilter(int filter, int window, int size, int samples_per_second, float freq) throws IllegalArgumentException {

		if (filter == filter_bandpass)
			throw new IllegalArgumentException("one parameter filter cannot be bandpass");

		cps = freq / samples_per_second;

		build(filter, window, size);
	}

	public AprsFilter(int filter, int window, int size, int samples_per_second, float low, float high) throws IllegalArgumentException {

		if (filter != filter_bandpass)
			throw new IllegalArgumentException("two parameter filter must be bandpass");

		cps_low = low / samples_per_second;
		cps_high = high / samples_per_second;
		/* middle of passband */
		cps = (cps_low + cps_high) / 2.0f;

		build(filter, window, size);
	}
}