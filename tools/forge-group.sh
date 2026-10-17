#!/usr/bin/env bash
# The Forge test groups, defined once for the load test and the game test:
#   tools/forge-group.sh A|B|C|D|none <folder>   download the group's newest 1.20.1 Forge jars into <folder>
#   tools/forge-group.sh A|B|C|D|none --ids      print the mod ids whose recipes the group should switch on
# A = Biomes O' Plenty with GlitchCore and TerraBlender; B = Ecologics (Wilder Wild and Promenade have
# no Forge build for 1.20.1); C = Beautify; D = the seven Macaw's mods. Needs curl and jq.
set -euo pipefail
MC=1.20.1
case ${1:-} in
	A) slugs="biomes-o-plenty glitchcore terrablender"; ids="biomesoplenty" ;;
	B) slugs="ecologics"; ids="ecologics" ;;
	C) slugs="beautify"; ids="beautify" ;;
	D) slugs="macaws-roofs macaws-fences-and-walls macaws-stairs macaws-windows macaws-furniture macaws-doors macaws-trapdoors"
		ids="mcwroofs mcwfences mcwstairs mcwwindows mcwfurnitures mcwdoors mcwtrpdoors" ;;
	none) slugs=""; ids="" ;;
	*) echo "usage: tools/forge-group.sh A|B|C|D|none <folder>|--ids" >&2; exit 2 ;;
esac
if [ "${2:-}" = --ids ]; then echo $ids | tr ' ' ','; exit 0; fi
dest=${2:?usage: tools/forge-group.sh A|B|C|D|none <folder>|--ids}
mkdir -p "$dest"
for s in $slugs; do
	# The newest release, or the newest beta when there's no release yet (as for GlitchCore and TerraBlender on 1.20.1).
	url=$(curl -fsS "https://api.modrinth.com/v2/project/$s/version?loaders=%5B%22forge%22%5D&game_versions=%5B%22$MC%22%5D" \
		| jq -r '([.[] | select(.version_type == "release")] + .)[0].files | (map(select(.primary)) + .)[0].url')
	curl -fsSL -o "$dest/$(basename "$url")" "$url"
done
