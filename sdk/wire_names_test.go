package sdk

import (
	"encoding/json"
	"testing"
)

// The Java records decode these payloads by component name, so a renamed JSON
// tag silently drops the field on the client.
func TestClientWireNamesMatchJavaRecords(t *testing.T) {
	cases := []struct {
		value any
		keys  []string
	}{
		{ClientScreenElement{
			ID: "e", Type: ClientScreenElementList, Disabled: true, Notify: true, Tooltip: "t", Wrap: true,
			Ellipsis: true, Align: "center", Items: []ClientScreenListItem{{ID: "a", Text: "A", Color: 1, Tooltip: "r"}},
			RowHeight: 12, Min: 1, Max: 2, Step: 1, Item: "minecraft:stone", Count: 2, Texture: "x",
			TextureWidth: 4, TextureHeight: 4, U: 1, V: 1,
		}, []string{"disabled", "notifyChanges", "tooltip", "wrap", "ellipsis", "align", "items", "rowHeight",
			"min", "max", "step", "item", "count", "texture", "textureWidth", "textureHeight", "u", "v"}},
		{ClientScreen{ID: "s", Scroll: true}, []string{"scroll"}},
		{HUDElement{Type: "panel", OverScreens: true, HideOnScreens: true, Background: 1, Padding: 1, LineSpacing: 1, Accent: 1},
			[]string{"overScreens", "hideOnScreens", "background", "padding", "lineSpacing", "accent"}},
		{MenuButton{ID: "b", Label: "B", Screen: MenuScreenPause, X: 1, Y: 1, Width: 1}, []string{"screen", "x", "y", "width"}},
	}
	for _, testCase := range cases {
		raw, err := json.Marshal(testCase.value)
		if err != nil {
			t.Fatal(err)
		}
		var fields map[string]any
		if err := json.Unmarshal(raw, &fields); err != nil {
			t.Fatal(err)
		}
		for _, key := range testCase.keys {
			if _, ok := fields[key]; !ok {
				t.Errorf("%T: missing JSON key %q in %s", testCase.value, key, raw)
			}
		}
	}

	var tick ClientTickEvent
	payload := `{"guiWidth":427,"screen":{"kind":"plugin","pluginId":"p","screenId":"s"},
		"target":{"type":"entity","entityUuid":"u","entityType":"minecraft:player","player":true},
		"keys":{"sprint":true,"sneak":true,"left":true},
		"player":{"health":20,"food":18,"mainHand":{"id":"minecraft:stone","count":3,"name":"Stone"}}}`
	if err := json.Unmarshal([]byte(payload), &tick); err != nil {
		t.Fatal(err)
	}
	if tick.GUIWidth != 427 || tick.Screen.ScreenID != "s" || !tick.Target.Player || !tick.Keys.Sprint ||
		!tick.Keys.Left || tick.Player.Food != 18 || tick.Player.MainHand.Count != 3 {
		t.Fatalf("tick decoded incorrectly: %+v", tick)
	}
}
