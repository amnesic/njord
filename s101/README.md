# S-101 viewing groups

Generates `server/src/nativeMain/resources/s101/viewing_groups.json`, which gives every style layer
a `metadata` object with its S-101 feature type, viewing group, viewing group layer and display
mode, and puts the S-101 display modes and viewing group layers in the style's root `metadata`.

```shell
python3 extract_viewing_groups.py
```

Standard library only, reads the IHO S-101 feature catalogue (S-57 acronyms are its `alias`
entries) and portrayal catalogue (`Rules/*.lua`, `portrayal_catalogue.xml`) at the commits pinned
at the top of the script. Bump those commits to follow a new catalogue release.

A client can then offer the S-101 modes by hiding layers above the selected one:

```js
const order = {DisplayBase: 1, StandardDisplay: 2, OtherInformation: 3};
for (const layer of map.getStyle().layers) {
  const mode = layer.metadata?.['s101:displayMode'];
  map.setLayoutProperty(layer.id, 'visibility', !mode || order[mode] <= order[selected] ? 'visible' : 'none');
}
```

or a custom selection by toggling `s101:viewingGroupLayer` (the names are in the root
`metadata`, `s101:viewingGroupLayers`). Text-only layers also carry `s101:textViewingGroup`,
for the S-101 Important Text / Names / Other Text switches.
