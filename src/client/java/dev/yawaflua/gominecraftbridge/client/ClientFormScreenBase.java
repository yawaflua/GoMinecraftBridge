package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenListItem;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenOption;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Version-independent state of a plugin screen. Subclasses in the version
 * overlays only translate rendering and input callbacks for their Minecraft API.
 */
abstract class ClientFormScreenBase extends Screen {
	protected static final int WIDTH = 300;
	protected static final int DEFAULT_ROW_HEIGHT = 12;
	protected final Screen parent;
	protected final ClientScreenSpec spec;
	protected final ClientScreenCallbacks callbacks;
	protected final List<ClientScreenElement> elements;
	protected final Map<String, EditBox> edits = new LinkedHashMap<>();
	protected final Map<String, SelectValue> selects = new LinkedHashMap<>();
	protected final Map<String, Checkbox> checkboxes = new LinkedHashMap<>();
	protected final Map<String, Slider> sliders = new LinkedHashMap<>();
	protected final Map<String, ListState> lists = new LinkedHashMap<>();
	protected final Map<AbstractWidget, Integer> positions = new LinkedHashMap<>();
	protected final List<ClientScreenElement> hitboxes = new ArrayList<>();
	private final Map<String, String> overrides = new HashMap<>();
	private String focusId;
	protected int scroll;
	protected int contentHeight;

	protected ClientFormScreenBase(Screen parent, ClientScreenSpec spec, ClientScreenCallbacks callbacks, String initialFocusId) {
		super(Component.literal(spec.title() == null ? "" : spec.title()));
		this.parent = parent;
		this.spec = spec;
		this.callbacks = callbacks;
		this.elements = new ArrayList<>(spec.elements());
		this.focusId = initialFocusId;
	}

	protected abstract void maskPassword(EditBox edit);

	String screenId() {
		return this.spec.id();
	}

	boolean customLayout() {
		return !this.elements.isEmpty();
	}

	Map<String, String> values() {
		Map<String, String> values = new LinkedHashMap<>();
		this.edits.forEach((id, edit) -> values.put(id, edit.getValue()));
		this.selects.forEach((id, select) -> values.put(id, select.value()));
		this.checkboxes.forEach((id, checkbox) -> values.put(id, Boolean.toString(checkbox.selected())));
		this.sliders.forEach((id, slider) -> values.put(id, slider.formatted()));
		this.lists.forEach((id, list) -> {
			if (list.selected != null) {
				values.put(id, list.selected);
			}
		});
		return Map.copyOf(values);
	}

	String focusedInputId() {
		for (Map.Entry<String, EditBox> entry : this.edits.entrySet()) {
			if (entry.getValue() == this.getFocused()) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Replaces elements by ID, appends unknown ones and drops removed IDs. Live
	 * input values, focus and list scroll survive unless the plugin sent a
	 * different value for that element.
	 */
	void update(List<ClientScreenElement> changed, List<String> removed) {
		if (!customLayout()) {
			throw new IllegalStateException("Only custom-element screens can be updated");
		}
		List<ClientScreenElement> next = new ArrayList<>(this.elements);
		Set<String> removedIds = new HashSet<>(removed);
		next.removeIf(element -> removedIds.contains(element.id()));
		for (ClientScreenElement element : changed) {
			int index = indexOf(next, element.id());
			if (index < 0) {
				next.add(element);
				continue;
			}
			if (!Objects.equals(next.get(index).value(), element.value())) {
				this.overrides.put(element.id(), element.value() == null ? "" : element.value());
			}
			next.set(index, element);
		}
		ClientScreenSpecValidator.validateElements(next);
		this.elements.clear();
		this.elements.addAll(next);
		this.focusId = focusedInputId();
		this.rebuildWidgets();
		this.setInitialFocus();
	}

	private static int indexOf(List<ClientScreenElement> elements, String id) {
		for (int index = 0; index < elements.size(); index++) {
			if (elements.get(index).id().equals(id)) {
				return index;
			}
		}
		return -1;
	}

	@Override
	protected void setInitialFocus() {
		EditBox edit = this.focusId == null ? null : this.edits.get(this.focusId);
		if (edit == null) {
			super.setInitialFocus();
			return;
		}
		this.setInitialFocus(edit);
	}

	@Override
	protected void init() {
		boolean rebuilding = !this.edits.isEmpty() || !this.selects.isEmpty() || !this.checkboxes.isEmpty()
				|| !this.sliders.isEmpty() || !this.lists.isEmpty();
		Map<String, String> live = rebuilding ? values() : Map.of();
		Map<String, Integer> cursors = new HashMap<>();
		this.edits.forEach((id, edit) -> cursors.put(id, edit.getCursorPosition()));
		Map<String, Integer> listScrolls = new HashMap<>();
		this.lists.forEach((id, list) -> listScrolls.put(id, list.scroll));
		String focused = focusedInputId();
		if (focused != null) {
			this.focusId = focused;
		}
		this.edits.clear();
		this.selects.clear();
		this.checkboxes.clear();
		this.sliders.clear();
		this.lists.clear();
		this.positions.clear();
		this.hitboxes.clear();
		if (customLayout()) {
			initCustomElements(live, cursors, listScrolls);
		} else {
			initForm(live);
		}
		this.overrides.clear();
	}

	private String initialValue(String id, String specValue, Map<String, String> live) {
		if (this.overrides.containsKey(id)) {
			return this.overrides.get(id);
		}
		if (live.containsKey(id)) {
			return live.get(id);
		}
		return specValue == null ? "" : specValue;
	}

	private void initForm(Map<String, String> live) {
		int x = this.width / 2 - WIDTH / 2;
		int y = 72;
		for (ClientScreenField field : this.spec.fields()) {
			String value = initialValue(field.id(), field.value(), live);
			if ("select".equals(field.type())) {
				SelectValue select = new SelectValue(field.options(), value);
				Button button = Button.builder(Component.literal(select.label()), pressed -> {
					select.next();
					pressed.setMessage(Component.literal(select.label()));
				}).bounds(x, y, WIDTH, 20).build();
				this.selects.put(field.id(), select);
				addPositioned(button, y);
			} else {
				EditBox edit = newEdit(field.type(), x, y, WIDTH, 20, Component.literal(field.label()));
				configureEdit(edit, field.type(), field.maxLength(), field.placeholder(), value);
				this.edits.put(field.id(), edit);
				addPositioned(edit, y);
			}
			y += 40;
		}
		for (ClientScreenButton definition : this.spec.buttons()) {
			Button button = Button.builder(Component.literal(definition.label()), pressed ->
					this.callbacks.button(definition, values())
			).bounds(x, y, WIDTH, 20).build();
			addPositioned(button, y);
			y += 24;
		}
		this.contentHeight = y;
		updatePositions();
	}

	private EditBox newEdit(String kind, int x, int y, int width, int height, Component label) {
		if (!kind.startsWith("number")) {
			return new EditBox(this.font, x, y, width, height, label);
		}
		return new EditBox(this.font, x, y, width, height, label) {
			@Override
			public void insertText(String text) {
				super.insertText(text.replaceAll("[^0-9-]", ""));
			}
		};
	}

	private void configureEdit(EditBox edit, String kind, int maxLength, String placeholder, String value) {
		edit.setMaxLength(maxLength == 0 ? 1_024 : maxLength);
		edit.setValue(value);
		if (placeholder != null) {
			edit.setHint(Component.literal(placeholder));
		}
		if (kind.startsWith("password")) {
			maskPassword(edit);
		}
	}

	private void initCustomElements(Map<String, String> live, Map<String, Integer> cursors, Map<String, Integer> listScrolls) {
		for (ClientScreenElement element : this.elements) {
			Bounds bounds = bounds(element);
			String value = initialValue(element.id(), element.value(), live);
			AbstractWidget widget = switch (element.type()) {
				case "hitbox" -> {
					this.hitboxes.add(element);
					yield null;
				}
				case "button" -> Button.builder(Component.literal(text(element)), pressed -> this.callbacks.button(
						new ClientScreenButton(element.id(), element.text(), element.close()), values()
				)).bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
				case "text_input", "number_input", "password_input" -> {
					EditBox edit = newEdit(
							element.type(), bounds.x(), bounds.y(), bounds.width(), bounds.height(),
							Component.literal(element.text() == null ? element.id() : element.text())
					);
					configureEdit(edit, element.type(), element.maxLength(), element.placeholder(), value);
					if (!this.overrides.containsKey(element.id()) && cursors.containsKey(element.id())) {
						edit.moveCursorTo(Math.min(cursors.get(element.id()), value.length()), false);
					}
					edit.setEditable(!element.disabled());
					if (element.notifyChanges()) {
						edit.setResponder(ignored -> this.callbacks.changed(element.id(), values()));
					}
					this.edits.put(element.id(), edit);
					yield edit;
				}
				case "select" -> {
					SelectValue select = new SelectValue(element.options(), value);
					this.selects.put(element.id(), select);
					yield Button.builder(Component.literal(select.label()), pressed -> {
						select.next();
						pressed.setMessage(Component.literal(select.label()));
						if (element.notifyChanges()) {
							this.callbacks.changed(element.id(), values());
						}
					}).bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build();
				}
				case "checkbox" -> {
					Checkbox checkbox = Checkbox.builder(Component.literal(text(element)), this.font)
							.pos(bounds.x(), bounds.y())
							.selected(Boolean.parseBoolean(value))
							.onValueChange((box, selected) -> {
								if (element.notifyChanges()) {
									this.callbacks.changed(element.id(), values());
								}
							})
							.build();
					this.checkboxes.put(element.id(), checkbox);
					yield checkbox;
				}
				case "slider" -> {
					Slider slider = new Slider(element, bounds, parseDouble(value, element.min()));
					this.sliders.put(element.id(), slider);
					yield slider;
				}
				case "list" -> {
					ListState list = new ListState(element, value.isEmpty() ? null : value);
					list.scroll = Math.min(listScrolls.getOrDefault(element.id(), 0), list.maxScroll(bounds));
					this.lists.put(element.id(), list);
					yield null;
				}
				default -> null;
			};
			if (widget == null) {
				continue;
			}
			if (!(widget instanceof EditBox)) {
				widget.active = !element.disabled();
			}
			if (element.tooltip() != null && !element.tooltip().isBlank()) {
				widget.setTooltip(Tooltip.create(Component.literal(element.tooltip())));
			}
			this.addRenderableWidget(widget);
		}
	}

	private static double parseDouble(String value, double fallback) {
		try {
			return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static String text(ClientScreenElement element) {
		return element.text() == null ? "" : element.text();
	}

	private void addPositioned(AbstractWidget widget, int y) {
		this.positions.put(widget, y);
		this.addRenderableWidget(widget);
	}

	protected void updatePositions() {
		int maximum = Math.max(0, this.contentHeight - (this.height - 32));
		this.scroll = Math.max(0, Math.min(this.scroll, maximum));
		for (Map.Entry<AbstractWidget, Integer> entry : this.positions.entrySet()) {
			int y = entry.getValue() - this.scroll;
			entry.getKey().setY(y);
			entry.getKey().visible = y >= 54 && y <= this.height - 26;
		}
	}

	@Override
	public void onClose() {
		this.callbacks.closed(values());
	}

	protected Bounds bounds(ClientScreenElement element) {
		int width;
		int height;
		switch (element.type()) {
			case "text" -> {
				List<FormattedCharSequence> lines = wrappedLines(element);
				width = element.width() == 0 ? this.font.width(text(element)) : element.width();
				height = element.height() == 0 ? this.font.lineHeight * Math.max(1, lines.size()) : element.height();
			}
			case "rectangle", "hitbox", "list", "image" -> {
				width = element.width();
				height = element.height();
			}
			case "item" -> {
				width = element.width() == 0 ? 16 : element.width();
				height = element.height() == 0 ? 16 : element.height();
			}
			case "checkbox" -> {
				width = element.width() == 0 ? 24 + this.font.width(text(element)) : element.width();
				height = element.height() == 0 ? 17 : element.height();
			}
			default -> {
				width = element.width() == 0 ? 150 : element.width();
				height = element.height() == 0 ? 20 : element.height();
			}
		}
		var position = ClientHudLayout.position(
				element.anchor(), element.x(), element.y(), width, height, this.width, this.height
		);
		return new Bounds(position.x(), position.y(), width, height);
	}

	private List<FormattedCharSequence> wrappedLines(ClientScreenElement element) {
		String text = text(element);
		if (element.wrap() && element.width() > 0) {
			return this.font.split(Component.literal(text), element.width());
		}
		if (element.ellipsis() && element.width() > 0 && this.font.width(text) > element.width()) {
			String ellipsis = "…";
			text = this.font.plainSubstrByWidth(text, Math.max(0, element.width() - this.font.width(ellipsis))) + ellipsis;
		}
		return List.of(Component.literal(text).getVisualOrderText());
	}

	protected List<TextLine> textLines(ClientScreenElement element) {
		Bounds bounds = bounds(element);
		List<TextLine> lines = new ArrayList<>();
		int y = bounds.y();
		for (FormattedCharSequence line : wrappedLines(element)) {
			int lineWidth = this.font.width(line);
			int x = switch (element.align() == null ? "left" : element.align()) {
				case "center" -> bounds.x() + (bounds.width() - lineWidth) / 2;
				case "right" -> bounds.x() + bounds.width() - lineWidth;
				default -> bounds.x();
			};
			lines.add(new TextLine(line, x, y));
			y += this.font.lineHeight;
		}
		return lines;
	}

	protected static int color(long color, int fallback) {
		return color == 0 ? fallback : (int) color;
	}

	protected boolean clickCustom(double mouseX, double mouseY) {
		for (ListState list : this.lists.values()) {
			Bounds bounds = bounds(list.element);
			if (!bounds.contains(mouseX, mouseY) || list.element.disabled()) {
				continue;
			}
			int row = list.rowAt(bounds, mouseY);
			if (row >= 0) {
				list.selected = list.element.items().get(row).id();
				this.callbacks.selected(list.element.id(), list.selected, values());
			}
			return true;
		}
		for (int index = this.hitboxes.size() - 1; index >= 0; index--) {
			ClientScreenElement element = this.hitboxes.get(index);
			if (!element.disabled() && bounds(element).contains(mouseX, mouseY)) {
				this.callbacks.button(new ClientScreenButton(element.id(), element.text(), element.close()), values());
				return true;
			}
		}
		return false;
	}

	protected boolean scrollCustom(double mouseX, double mouseY, double vertical) {
		for (ListState list : this.lists.values()) {
			Bounds bounds = bounds(list.element);
			if (bounds.contains(mouseX, mouseY)) {
				list.scroll = Math.max(0, Math.min(list.scroll - (int) Math.signum(vertical), list.maxScroll(bounds)));
				return true;
			}
		}
		if (this.spec.scroll()) {
			this.callbacks.scrolled(vertical > 0 ? "up" : "down", values());
			return true;
		}
		return false;
	}

	protected boolean submitFocused() {
		String id = focusedInputId();
		if (id == null) {
			return false;
		}
		this.callbacks.submitted(id, values());
		return true;
	}

	protected String hoveredTooltip(int mouseX, int mouseY) {
		for (ListState list : this.lists.values()) {
			Bounds bounds = bounds(list.element);
			if (bounds.contains(mouseX, mouseY)) {
				int row = list.rowAt(bounds, mouseY);
				if (row >= 0 && list.element.items().get(row).tooltip() != null) {
					return list.element.items().get(row).tooltip();
				}
			}
		}
		for (ClientScreenElement element : this.elements) {
			if (element.tooltip() == null || element.tooltip().isBlank()) {
				continue;
			}
			switch (element.type()) {
				case "text", "rectangle", "hitbox", "item", "image", "list" -> {
					if (bounds(element).contains(mouseX, mouseY)) {
						return element.tooltip();
					}
				}
				default -> {
				}
			}
		}
		return null;
	}

	protected record Bounds(int x, int y, int width, int height) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	protected record TextLine(FormattedCharSequence text, int x, int y) {
	}

	protected static final class ListState {
		final ClientScreenElement element;
		String selected;
		int scroll;

		private ListState(ClientScreenElement element, String selected) {
			this.element = element;
			this.selected = selected;
		}

		int rowHeight() {
			return this.element.rowHeight() == 0 ? DEFAULT_ROW_HEIGHT : this.element.rowHeight();
		}

		int visibleRows(Bounds bounds) {
			return Math.max(1, bounds.height() / rowHeight());
		}

		int maxScroll(Bounds bounds) {
			return Math.max(0, this.element.items().size() - visibleRows(bounds));
		}

		int rowAt(Bounds bounds, double mouseY) {
			int row = this.scroll + (int) ((mouseY - bounds.y()) / rowHeight());
			return row >= 0 && row < this.element.items().size() ? row : -1;
		}

		List<ClientScreenListItem> visibleItems(Bounds bounds) {
			int end = Math.min(this.element.items().size(), this.scroll + visibleRows(bounds));
			return this.element.items().subList(Math.min(this.scroll, end), end);
		}
	}

	protected final class Slider extends AbstractSliderButton {
		private final ClientScreenElement element;

		private Slider(ClientScreenElement element, Bounds bounds, double initial) {
			super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty(), 0);
			this.element = element;
			this.value = (clamp(initial) - element.min()) / (element.max() - element.min());
			updateMessage();
		}

		private double clamp(double raw) {
			double value = Math.max(this.element.min(), Math.min(this.element.max(), raw));
			if (this.element.step() > 0) {
				value = this.element.min() + Math.round((value - this.element.min()) / this.element.step()) * this.element.step();
			}
			return Math.max(this.element.min(), Math.min(this.element.max(), value));
		}

		double current() {
			return clamp(this.element.min() + this.value * (this.element.max() - this.element.min()));
		}

		String formatted() {
			double current = current();
			return current == Math.rint(current) && this.element.step() >= 1
					? Long.toString((long) current)
					: Double.toString(current);
		}

		@Override
		protected void updateMessage() {
			String label = this.element.text() == null || this.element.text().isBlank() ? "" : this.element.text() + ": ";
			this.setMessage(Component.literal(label + formatted()));
		}

		@Override
		protected void applyValue() {
			if (this.element.notifyChanges()) {
				ClientFormScreenBase.this.callbacks.changed(this.element.id(), values());
			}
		}
	}

	protected static final class SelectValue {
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
