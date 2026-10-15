#!/usr/bin/env bash
# CI server load tests. Each one starts a real dedicated server with Cozy Core and fails unless the
# world loads with no data errors.
#   tools/ci-load-test.sh fabric   [none|A|B|C|D] <wood mods folder> the Fabric jar (+ Fabric API)
#   tools/ci-load-test.sh neoforge [none|A|B|D]                      the NeoForge jar
#   tools/ci-load-test.sh vanilla                                  the data pack zip on plain vanilla
# The Fabric groups are tools/group-jars.sh's: A and B wood mods, C Beautify, D Macaw's.
# On NeoForge, Ecologics is in B, and D is Macaw's NeoForge versions; there is no C yet: Beautify ReFoxed 2.0.0 crashes on NeoForge
# 26.3's release builds by itself (ModConfig$Type.SERVER, 2026-10-13).
# Expects dist/ (node tools/build.js, ./gradlew build, node tools/build.js --jars) and, for vanilla, ~/mc/server-bundler.jar (the CI build job).
set -euo pipefail
cd "$(dirname "$0")/.."
LOADER=$1 GROUP=${2:-none} WOOD=${3:-}
MC=26.3 FABRIC_LOADER=0.19.5 FABRIC_INSTALLER=1.1.2
FABRIC_JAR=$(ls dist/cozy-core-*-fabric.jar | head -1)
NEOFORGE_JAR=$(ls dist/cozy-core-*-neoforge.jar | head -1)
ZIP=$(ls dist/cozy-core-*[0-9].zip | head -1)
RUN=$(mktemp -d)

modrinth_jar() { # newest Modrinth file of a project for a loader and Minecraft version -> folder
	# (the newest release, or the newest beta when there's no release yet, as for BOP on NeoForge 26.3)
	local url
	url=$(curl -fsS "https://api.modrinth.com/v2/project/$1/version?loaders=%5B%22$2%22%5D&game_versions=%5B%22$MC%22%5D" \
		| jq -r '([.[] | select(.version_type == "release")] + .)[0].files | (map(select(.primary)) + .)[0].url')
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
		cp "$FABRIC_JAR" "$RUN/mods/"
		for j in $(bash tools/group-jars.sh "$GROUP" "$WOOD"); do cp "$WOOD/$j" "$RUN/mods/"; done
		# FrozenLib (group B) logs harmless config-entry errors on a first start. On Fabric the lines
		# come from the "main" logger and don't name FrozenLib.
		if [ "$GROUP" = B ]; then export IGNORE='\]: Unable to read config entry \w+$'; fi
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
		# The newest NeoForge release for this Minecraft version, installed as a server.
		NEOFORGE=$(curl -fsS https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml \
			| grep -o "<version>$MC\.[0-9.]*</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
		curl -fsSL -o "$RUN/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
		(cd "$RUN" && java -jar installer.jar --install-server . > /dev/null)
		mkdir -p "$RUN/mods"
		cp "$NEOFORGE_JAR" "$RUN/mods/"
		# NeoForge groups (as tested): A = Biomes O' Plenty with GlitchCore and TerraBlender;
		# B = Ecologics, Wilder Wild with FrozenLib, and Promenade; D = the seven Macaw's mods.
		case $GROUP in
			A) slugs="biomes-o-plenty glitchcore terrablender" ;;
			B) slugs="ecologics wilder-wild frozenlib promenade"; export IGNORE='FrozenLib.*Unable to read config' ;;
			D) slugs="macaws-roofs macaws-fences-and-walls macaws-stairs macaws-windows macaws-furniture macaws-doors macaws-trapdoors" ;;
			*) slugs="" ;;
		esac
		for s in $slugs; do modrinth_jar "$s" neoforge "$RUN/mods"; done
		server_files
		echo "NeoForge $NEOFORGE, group $GROUP: $(ls "$RUN/mods" | tr '\n' ' ')"
		node tools/server-test.js "$RUN" java "@libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt" "datapack list enabled"
		;;
	*) echo "usage: tools/ci-load-test.sh fabric|neoforge|vanilla [none|A|B|C|D] [wood folder]"; exit 2 ;;
esac
