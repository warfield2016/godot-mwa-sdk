@tool
extends EditorPlugin

## SolanaMWA Godot Plugin
##
## Registers the native Android MWA plugin with Godot's export template.
## The actual MWA functionality is provided via the GDExtension + Android AAR.

const PLUGIN_NAME = "SolanaMWA"


func _enter_tree() -> void:
	# The plugin is registered via the .gdextension file + AAR.
	# This EditorPlugin is primarily a marker for the Godot editor.
	pass


func _exit_tree() -> void:
	pass


func get_plugin_name() -> String:
	return PLUGIN_NAME
