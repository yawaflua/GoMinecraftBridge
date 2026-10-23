package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenElement;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenField;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenListItem;
import dev.yawaflua.gominecraftbridge.protocol.ClientScreenSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class ClientFormScreen extends ClientFormScreenBase {
	ClientFormScreen(Screen parent, ClientScreenSpec spec, ClientScreenCallbacks callbacks, String initialFocusId) {
		super(parent, spec, callbacks, initialFocusId);
	}

	@Override
	protected void maskPassword(EditBox edit) {
		edit.setFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return customLayout() && clickCustom(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical != 0 && customLayout()) {
			return scrollCustom(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		if (vertical != 0) {
			this.scroll -= (int) Math.signum(vertical) * 24;
			updatePositions();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (isEnter(key) && customLayout() && submitFocused()) {
			return true;
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	private static boolean isEnter(int key) {
		return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		if (customLayout()) {
			renderCustomElements(graphics, mouseX, mouseY);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		if (customLayout()) {
			String tooltip = hoveredTooltip(mouseX, mouseY);
			if (tooltip != null) {
				graphics.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
			}
			return;
		}
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xffffffff);
		if (this.spec.body() != null && !this.spec.body().isBlank()) {
			graphics.drawCenteredString(this.font, this.spec.body(), this.width / 2, 38, 0xffbfbfbf);
		}
		int index = 0;
		for (ClientScreenField field : this.spec.fields()) {
			int y = 60 + index * 40 - this.scroll;
			if (y >= 48 && y < this.height - 30) {
				graphics.drawString(this.font, field.label(), this.width / 2 - WIDTH / 2, y, 0xffd0d0d0, false);
			}
			index++;
		}
	}

	private void renderCustomElements(GuiGraphics graphics, int mouseX, int mouseY) {
		for (ClientScreenElement element : this.elements) {
			switch (element.type()) {
				case "rectangle" -> {
					Bounds bounds = bounds(element);
					graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), (int) element.color());
				}
				case "text" -> {
					int color = color(element.color(), 0xffffffff);
					for (TextLine line : textLines(element)) {
						graphics.drawString(this.font, line.text(), line.x(), line.y(), color, element.shadow());
					}
				}
				case "item" -> {
					Bounds bounds = bounds(element);
					ItemStack stack = ClientVersionAdapter.itemStack(element.item(), element.count());
					graphics.renderItem(stack, bounds.x(), bounds.y());
					graphics.renderItemDecorations(this.font, stack, bounds.x(), bounds.y());
				}
				case "image" -> {
					Bounds bounds = bounds(element);
					int textureWidth = element.textureWidth() == 0 ? bounds.width() : element.textureWidth();
					int textureHeight = element.textureHeight() == 0 ? bounds.height() : element.textureHeight();
					graphics.blit(ClientVersionAdapter.parse(element.texture()), bounds.x(), bounds.y(),
							(float) element.u(), (float) element.v(), bounds.width(), bounds.height(), textureWidth, textureHeight);
				}
				case "list" -> renderList(graphics, this.lists.get(element.id()), mouseX, mouseY);
				default -> {
				}
			}
		}
	}

	private void renderList(GuiGraphics graphics, ListState list, int mouseX, int mouseY) {
		if (list == null) {
			return;
		}
		Bounds bounds = bounds(list.element);
		int right = bounds.x() + bounds.width();
		int bottom = bounds.y() + bounds.height();
		graphics.fill(bounds.x(), bounds.y(), right, bottom, 0x80000000);
		graphics.enableScissor(bounds.x(), bounds.y(), right, bottom);
		int rowHeight = list.rowHeight();
		int y = bounds.y();
		int hovered = bounds.contains(mouseX, mouseY) ? list.rowAt(bounds, mouseY) : -1;
		int row = list.scroll;
		for (ClientScreenListItem item : list.visibleItems(bounds)) {
			if (item.id().equals(list.selected)) {
				graphics.fill(bounds.x(), y, right, y + rowHeight, 0x80ffffff);
			} else if (row == hovered && !list.element.disabled()) {
				graphics.fill(bounds.x(), y, right, y + rowHeight, 0x40ffffff);
			}
			String text = item.text() == null ? "" : item.text();
			int textWidth = bounds.width() - 8;
			if (this.font.width(text) > textWidth) {
				text = this.font.plainSubstrByWidth(text, Math.max(0, textWidth - this.font.width("…"))) + "…";
			}
			int textY = y + (rowHeight - this.font.lineHeight) / 2 + 1;
			graphics.drawString(this.font, text, bounds.x() + 4, textY, color(item.color(), color(list.element.color(), 0xffffffff)), true);
			y += rowHeight;
			row++;
		}
		graphics.disableScissor();
		int maxScroll = list.maxScroll(bounds);
		if (maxScroll > 0) {
			int track = bounds.height();
			int thumb = Math.max(8, track * list.visibleRows(bounds) / list.element.items().size());
			int thumbY = bounds.y() + (track - thumb) * list.scroll / maxScroll;
			graphics.fill(right - 3, bounds.y(), right, bottom, 0x60000000);
			graphics.fill(right - 3, thumbY, right, thumbY + thumb, 0xffa0a0a0);
		}
	}
}
