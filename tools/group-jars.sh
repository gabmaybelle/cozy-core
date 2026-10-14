#!/usr/bin/env bash
# The jars of one Fabric test group, from a folder holding all the test mods (tools/fetch-test-mods.js):
#   tools/group-jars.sh A|B|C|none <folder>
# A = Biomes O' Plenty (with GlitchCore and TerraBlender) and Ecologics; B = Wilder Wild (with
# FrozenLib) and Promenade; C = Beautify (with Cloth Config). Biolith, bundled in Wilder Wild and
# Promenade, crashes with TerraBlender, so A and B never share a game. Prints one file name per line.
case $1 in
	A) ls "$2" | grep -E '^(BiomesOPlenty|GlitchCore|TerraBlender|Ecologics)' ;;
	B) ls "$2" | grep -E '^(WilderWild|FrozenLib|promenade)' ;;
	C) ls "$2" | grep -E '^(beautify|cloth-config)' ;;
	none) true ;;
	*) echo "usage: tools/group-jars.sh A|B|C|none <folder>" >&2; exit 2 ;;
esac
