package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenOption;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ClientFormScreen extends Screen {
	private static final int WIDTH = 300;
	private final Screen parent;
	private final ClientScreenSpec spec;
	private final ClientScreenCallbacks callbacks;
	private final Map<String, EditBox> edits = new LinkedHashMap<>();
	private final Map<String, SelectValue> selects = new LinkedHashMap<>();
	private final Map<AbstractWidget, Integer> positions = new LinkedHashMap<>();
	private final List<ClientScreenElement> hitboxes = new ArrayList<>();
	private int scroll;
	private int contentHeight;

	ClientFormScreen(Screen parent, ClientScreenSpec spec, ClientScreenCallbacks callbacks) {
		super(Component.literal(spec.title() == null ? "" : spec.title()));
		this.parent = parent;
		this.spec = spec;
		this.callbacks = callbacks;
	}

	String screenId() {
		return this.spec.id();
	}

	Map<String, String> values() {
		Map<String, String> values = new LinkedHashMap<>();
		this.edits.forEach((id, edit) -> values.put(id, edit.getValue()));
		this.selects.forEach((id, select) -> values.put(id, select.value()));
		return Map.copyOf(values);
	}

	@Override
	protected void init() {
		this.edits.clear();
		this.selects.clear();
		this.positions.clear();
		this.hitboxes.clear();
		if (!this.spec.elements().isEmpty()) {
			initCustomElements();
			return;
		}
		int x = this.width / 2 - WIDTH / 2;
		int y = 72;
		for (ClientScreenField field : this.spec.fields()) {
			if ("select".equals(field.type())) {
				SelectValue select = new SelectValue(field.options(), field.value());
				Button button = Button.builder(Component.literal(select.label()), ignored -> {
					select.next();
					ignored.setMessage(Component.literal(select.label()));
				}).bounds(x, y, WIDTH, 20).build();
				this.selects.put(field.id(), select);
				addPositioned(button, y);
			} else {
				EditBox edit = new EditBox(this.font, x, y, WIDTH, 20, Component.literal(field.label()));
				edit.setMaxLength(field.maxLength() == 0 ? 1_024 : field.maxLength());
				edit.setValue(field.value() == null ? "" : field.value());
				if (field.placeholder() != null) {
					edit.setHint(Component.literal(field.placeholder()));
				}
				if ("number".equals(field.type())) {
					edit.setFilter(value -> value.isEmpty() || value.matches("-?\\d*"));
				}
				if ("password".equals(field.type())) {
					edit.setFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
				}
				this.edits.put(field.id(), edit);
				addPositioned(edit, y);
			}
			y += 40;
		}
		for (ClientScreenButton definition : this.spec.buttons()) {
			Button button = Button.builder(Component.literal(definition.label()), ignored ->
					this.callbacks.button(definition, values())
			).bounds(x, y, WIDTH, 20).build();
			addPositioned(button, y);
			y += 24;
		}
		this.contentHeight = y;
		updatePositions();
	}

	private void initCustomElements() {
		for (ClientScreenElement element : this.spec.elements()) {
			int width = element.width() == 0 ? 150 : element.width();
			int height = element.height() == 0 ? 20 : element.height();
			var position = ClientHudLayout.position(
					element.anchor(), element.x(), element.y(), width, height, this.width, this.height
			);
				switch (element.type()) {
				case "hitbox" -> this.hitboxes.add(element);
				case "button" -> this.addRenderableWidget(Button.builder(
						Component.literal(element.text() == null ? "" : element.text()),
						ignored -> this.callbacks.button(
								new ClientScreenButton(element.id(), element.text(), element.close()), values()
						)
				).bounds(position.x(), position.y(), width, height).build());
				case "text_input", "number_input", "password_input" -> {
					EditBox edit = new EditBox(
							this.font, position.x(), position.y(), width, height,
							Component.literal(element.text() == null ? element.id() : element.text())
					);
					edit.setMaxLength(element.maxLength() == 0 ? 1_024 : element.maxLength());
					edit.setValue(element.value() == null ? "" : element.value());
					if (element.placeholder() != null) {
						edit.setHint(Component.literal(element.placeholder()));
					}
					if ("number_input".equals(element.type())) {
						edit.setFilter(value -> value.isEmpty() || value.matches("-?\\d*"));
					}
					if ("password_input".equals(element.type())) {
						edit.setFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
					}
					this.edits.put(element.id(), edit);
					this.addRenderableWidget(edit);
				}
				case "select" -> {
					SelectValue select = new SelectValue(element.options(), element.value());
					Button button = Button.builder(Component.literal(select.label()), ignored -> {
						select.next();
						ignored.setMessage(Component.literal(select.label()));
					}).bounds(position.x(), position.y(), width, height).build();
					this.selects.put(element.id(), select);
					this.addRenderableWidget(button);
				}
				default -> {
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		for (int index = this.hitboxes.size() - 1; index >= 0; index--) {
			ClientScreenElement element = this.hitboxes.get(index);
			var position = ClientHudLayout.position(
					element.anchor(), element.x(), element.y(), element.width(), element.height(), this.width, this.height
			);
			if (mouseX >= position.x() && mouseX < position.x() + element.width()
					&& mouseY >= position.y() && mouseY < position.y() + element.height()) {
				this.callbacks.button(new ClientScreenButton(element.id(), element.text(), element.close()), values());
				return true;
			}
		}
		return false;
	}

	private void addPositioned(AbstractWidget widget, int y) {
		this.positions.put(widget, y);
		this.addRenderableWidget(widget);
	}

	private void updatePositions() {
		int maximum = Math.max(0, this.contentHeight - (this.height - 32));
		this.scroll = Math.max(0, Math.min(this.scroll, maximum));
		for (Map.Entry<AbstractWidget, Integer> entry : this.positions.entrySet()) {
			int y = entry.getValue() - this.scroll;
			entry.getKey().setY(y);
			entry.getKey().visible = y >= 54 && y <= this.height - 26;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0) {
			this.scroll -= (int) Math.signum(vertical) * 24;
			updatePositions();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void onClose() {
		this.callbacks.closed(values());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (!this.spec.elements().isEmpty()) {
			renderCustomElements(graphics);
			super.render(graphics, mouseX, mouseY, delta);
			return;
		}
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xffffff);
		if (this.spec.body() != null && !this.spec.body().isBlank()) {
			graphics.drawCenteredString(this.font, this.spec.body(), this.width / 2, 38, 0xbfbfbf);
		}
		int index = 0;
		for (ClientScreenField field : this.spec.fields()) {
			int y = 60 + index * 40 - this.scroll;
			if (y >= 48 && y < this.height - 30) {
				graphics.drawString(this.font, field.label(), this.width / 2 - WIDTH / 2, y, 0xd0d0d0, false);
			}
			index++;
		}
	}

	private void renderCustomElements(GuiGraphics graphics) {
		for (ClientScreenElement element : this.spec.elements()) {
			if (!element.type().equals("text") && !element.type().equals("rectangle")) {
				continue;
			}
			int width = element.type().equals("text")
					? (element.width() == 0 ? this.font.width(element.text() == null ? "" : element.text()) : element.width())
					: element.width();
			int height = element.height() == 0 ? this.font.lineHeight : element.height();
			var position = ClientHudLayout.position(
					element.anchor(), element.x(), element.y(), width, height, this.width, this.height
			);
			if (element.type().equals("rectangle")) {
				graphics.fill(position.x(), position.y(), position.x() + width, position.y() + height, (int) element.color());
			} else {
				int color = element.color() == 0 ? 0xffffffff : (int) element.color();
				graphics.drawString(this.font, element.text() == null ? "" : element.text(), position.x(), position.y(), color, element.shadow());
			}
		}
	}

	private static final class SelectValue {
		private final List<ClientScreenOption> options;
		private int index;

		private SelectValue(List<ClientScreenOption> options, String initial) {
			this.options = options;
			for (int i = 0; i < options.size(); i++) {
				if (options.get(i).value().equals(initial)) {
					this.index = i;
					break;
				}
			}
		}

		private void next() {
			this.index = (this.index + 1) % this.options.size();
		}

		private String label() {
			return this.options.get(this.index).label();
		}

		private String value() {
			return this.options.get(this.index).value();
		}
	}
}
