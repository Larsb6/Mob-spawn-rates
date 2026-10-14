package com.larsb6.mobspawnrates.client;

import com.larsb6.mobspawnrates.config.RatesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.function.DoubleConsumer;

/**
 * Slider for a rate between 0x and 20x, snapped to 0.01.
 * The left half of the slider covers 0x-2x for fine control, the right half covers 2x-20x.
 * When focused, the arrow keys change the value by 0.01 (0.1 with Shift).
 */
public class RateSlider extends AbstractSliderButton {
	private static final double KNEE = 2.0;

	private final String label;
	private final DoubleConsumer onChange;
	private double rate;

	public RateSlider(int width, String label, double rate, DoubleConsumer onChange) {
		super(0, 0, width, 20, Component.empty(), toSlider(rate));
		this.label = label;
		this.onChange = onChange;
		this.rate = RatesConfig.clamp(rate);
		updateMessage();
	}

	static double toSlider(double rate) {
		if (rate <= KNEE) {
			return rate / KNEE * 0.5;
		}
		return 0.5 + (rate - KNEE) / (RatesConfig.MAX - KNEE) * 0.5;
	}

	static double toRate(double slider) {
		double rate = slider <= 0.5
				? slider / 0.5 * KNEE
				: KNEE + (slider - 0.5) / 0.5 * (RatesConfig.MAX - KNEE);
		return RatesConfig.clamp(rate);
	}

	public double getRate() {
		return rate;
	}

	/** Updates the slider without notifying the listener (used for server syncs). */
	public void setRateSilently(double newRate) {
		this.rate = RatesConfig.clamp(newRate);
		this.value = toSlider(this.rate);
		updateMessage();
	}

	private void setRate(double newRate) {
		double clamped = RatesConfig.clamp(newRate);
		if (clamped != rate) {
			rate = clamped;
			onChange.accept(rate);
		}
		this.value = toSlider(rate);
		updateMessage();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.isFocused() && (event.isLeft() || event.isRight())) {
			double step = event.hasShiftDown() ? 0.1 : 0.01;
			setRate(rate + (event.isLeft() ? -step : step));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void updateMessage() {
		ChatFormatting color = rate == 1.0 ? ChatFormatting.WHITE : rate < 1.0 ? ChatFormatting.RED : ChatFormatting.GREEN;
		setMessage(Component.literal(label + ": ").append(Component.literal("x" + format(rate)).withStyle(color)));
	}

	@Override
	protected void applyValue() {
		setRate(toRate(this.value));
	}

	public static String format(double value) {
		return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0).stripTrailingZeros().toPlainString();
	}
}
