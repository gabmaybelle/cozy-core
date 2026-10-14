#!/usr/bin/env bash
# CI server load tests. Each one starts a real dedicated server with Cozy Core and fails unless the
# world loads with no data errors.
#   tools/ci-load-test.sh fabric   [none|A|B] <wood mods folder>   the mod on Fabric (+ Fabric API)
#   tools/ci-load-test.sh neoforge [none|A|B]                      the mod on NeoForge
#   tools/ci-load-test.sh vanilla                                  the data pack zip on plain vanilla
# Wood-mod groups: A = Biomes O' Plenty (with GlitchCore and TerraBlender) and Ecologics;
# B = Wilder Wild (with FrozenLib) and Promenade. Biolith, bundled in Wilder Wild and Promenade,
# crashes with TerraBlender, so the two groups never share a server.
# Expects dist/ (node tools/build.js) and, for vanilla, ~/mc/server-bundler.jar (the CI build job).
set -euo pipefail
cd "$(dirname "$0")/.."
LOADER=$1 GROUP=${2:-none} WOOD=${3:-}
MC=26.3 FABRIC_LOADER=0.19.5 FABRIC_INSTALLER=1.1.2
JAR=$(ls dist/cozy-core-*-fabric-neoforge.jar | head -1)
ZIP=$(ls dist/cozy-core-*[0-9].zip | head -1)
RUN=$(mktemp -d)

group_jars() { # the wood-mod jars of a group, from a folder of all of them
	case $GROUP in
		A) ls "$WOOD" | grep -E '^(BiomesOPlenty|GlitchCore|TerraBlender|Ecologics)' ;;
		B) ls "$WOOD" | grep -E '^(WilderWild|FrozenLib|promenade)' ;;
		*) true ;;
	esac
}

modrinth_jar() { # newest Modrinth file of a project for a loader and Minecraft version -> folder
	local url
	url=$(curl -fsS "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D&game_versions=%5B%22$MC%22%5D" \
		| jq -r '[.[] | select(.version_type == "release")][0].files | (map(select(.primary)) + .)[0].url')
	curl -fsSL -o "$3/$(basename "$url")" "$url"
}

server_files() { # eula and an offline server.properties
	echo 'eula=true' > "$RUN/eula.txt"
	printf 'online-mode=false\nspawn-protection=0\nview-distance=4\nsimulation-distance=4\n' > "$RUN/server.properties"
}

case $LOADER in
	fabric)
		mkdir -p "$RUN/mods"
		curl -fsSL -o "$RUN/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$FABRIC_LOADER/$FABRIC_INSTALLER/server/jar"
		modrinth_jar fabric-api fabric "$RUN/mods"
		cp "$JAR" "$RUN/mods/"
		for j in $(group_jars); do cp "$WOOD/$j" "$RUN/mods/"; done
		server_files
		echo "Fabric $MC, group $GROUP: $(ls "$RUN/mods" | tr '\n' ' ')"
		node tools/server-test.js "$RUN" java "$RUN/server.jar" "datapack list enabled"
		;;
	vanilla)
		mkdir -p "$RUN/world/datapacks"
		cp ~/mc/server-bundler.jar "$RUN/server.jar"
		cp "$ZIP" "$RUN/world/datapacks/"
		server_files
		out=$(node tools/server-test.js "$RUN" java "$RUN/server.jar" "datapack list enabled") || { echo "$out"; exit 1; }
		echo "$out"
		# The data pack must be on, not just present.
		echo "$out" | grep -q "file/$(basename "$ZIP")" || { echo "the Cozy Core data pack isn't enabled"; exit 1; }
		;;
	neoforge)
		mkdir -p tools/.cache
		echo "Gabby agreed to the Minecraft EULA for local test servers; CI runs those same tests." > tools/.cache/eula-accepted
		case $GROUP in
			A) extra=(--modrinth biomes-o-plenty,terrablender,ecologics) ;;
			B) extra=(--modrinth promenade,wilder-wild --ignore 'FrozenLib.*Unable to read config') ;;
			*) extra=() ;;
		esac
		node tools/neoforge-server-test.js "$RUN" --fresh-world --mod "$JAR" "${extra[@]}"
		;;
	*) echo "usage: tools/ci-load-test.sh fabric|neoforge|vanilla [none|A|B] [wood folder]"; exit 2 ;;
esac
