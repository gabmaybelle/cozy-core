#!/usr/bin/env bash
# CI server load tests. Each one starts a real dedicated server with Cozy Core and fails unless the
# world loads with no data errors.
#   tools/ci-load-test.sh fabric   [none|A|B|C|D] <wood mods folder> the Fabric jar (+ Fabric API)
#   tools/ci-load-test.sh neoforge [none|A|B|C|D]                    the NeoForge jar
#   tools/ci-load-test.sh vanilla                                  the data pack zip on plain vanilla
# The Fabric groups are tools/group-jars.sh's: A and B wood mods, C Beautify, D Macaw's.
# The NeoForge groups are tools/neoforge-group.sh's (Ecologics is in B there, with no Wilder Wild).
# Expects dist/ (node tools/build.js, ./gradlew build, node tools/build.js --jars) and, for vanilla, ~/mc/server-bundler.jar (the CI build job).
set -euo pipefail
cd "$(dirname "$0")/.."
LOADER=$1 GROUP=${2:-none} WOOD=${3:-}
MC=1.21.1 NEOFORGE_LINE=21.1 FABRIC_LOADER=0.19.5 FABRIC_INSTALLER=1.1.2
FABRIC_JAR=$(ls dist/cozy-core-*-fabric.jar | head -1)
NEOFORGE_JAR=$(ls dist/cozy-core-*-neoforge.jar | head -1)
ZIP=$(ls dist/cozy-core-*[0-9].zip | head -1)
RUN=$(mktemp -d)

modrinth_jar() { # newest Modrinth file of a project for a loader and Minecraft version -> folder
	# (the newest release, or the newest beta when there's no release yet, as for BOP on 1.21.1)
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
		# The newest NeoForge release for this Minecraft version (21.1.x for 1.21.1), installed as a server.
		NEOFORGE=$(curl -fsS https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml \
			| grep -o "<version>$NEOFORGE_LINE\.[0-9.]*</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
		curl -fsSL -o "$RUN/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
		(cd "$RUN" && java -jar installer.jar --install-server . > /dev/null)
		mkdir -p "$RUN/mods"
		cp "$NEOFORGE_JAR" "$RUN/mods/"
		# The groups are tools/neoforge-group.sh's (shared with the NeoForge game test).
		bash tools/neoforge-group.sh "$GROUP" "$RUN/mods"
		# Beautify's NeoForge build for 1.21.1 logs errors about its own loot tables (trellises, lamp jar, hanging pot).
		if [ "$GROUP" = C ]; then export IGNORE='LootDataType.*beautify:blocks/'; fi
		server_files
		echo "NeoForge $NEOFORGE, group $GROUP: $(ls "$RUN/mods" | tr '\n' ' ')"
		node tools/server-test.js "$RUN" java "@libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt" "datapack list enabled"
		;;
	*) echo "usage: tools/ci-load-test.sh fabric|neoforge|vanilla [none|A|B|C|D] [wood folder]"; exit 2 ;;
esac
