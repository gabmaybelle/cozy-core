#!/usr/bin/env bash
# The NeoForge test groups, defined once for the load test and the game test:
#   tools/neoforge-group.sh A|B|D|none <folder>   download the group's newest 26.3 NeoForge jars into <folder>
#   tools/neoforge-group.sh A|B|D|none --ids      print the mod ids whose recipes the group should switch on
# A = Biomes O' Plenty with GlitchCore and TerraBlender; B = Ecologics, Wilder Wild with FrozenLib,
# and Promenade; D = the seven Macaw's mods. No C: Beautify ReFoxed 2.0.0 crashes on NeoForge 26.3's
# release builds by itself (ModConfig$Type.SERVER, 2026-10-13). Needs curl and jq.
set -euo pipefail
MC=26.3
case ${1:-} in
	A) slugs="biomes-o-plenty glitchcore terrablender"; ids="biomesoplenty" ;;
	B) slugs="ecologics wilder-wild frozenlib promenade"; ids="ecologics wilderwild promenade" ;;
	D) slugs="macaws-roofs macaws-fences-and-walls macaws-stairs macaws-windows macaws-furniture macaws-doors macaws-trapdoors"
		ids="mcwroofs mcwfences mcwstairs mcwwindows mcwfurnitures mcwdoors mcwtrpdoors" ;;
	none) slugs=""; ids="" ;;
	*) echo "usage: tools/neoforge-group.sh A|B|D|none <folder>|--ids" >&2; exit 2 ;;
esac
if [ "${2:-}" = --ids ]; then echo $ids | tr ' ' ','; exit 0; fi
dest=${2:?usage: tools/neoforge-group.sh A|B|D|none <folder>|--ids}
mkdir -p "$dest"
for s in $slugs; do
	# The newest release, or the newest beta when there's no release yet (as for BOP on NeoForge 26.3).
	url=$(curl -fsS "https://api.modrinth.com/v2/project/$s/version?loaders=%5B%22neoforge%22%5D&game_versions=%5B%22$MC%22%5D" \
		| jq -r '([.[] | select(.version_type == "release")] + .)[0].files | (map(select(.primary)) + .)[0].url')
	curl -fsSL -o "$dest/$(basename "$url")" "$url"
done
