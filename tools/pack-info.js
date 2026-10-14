// What the generator and the build both need to know about the pack, in one place. The version
// is gradle.properties' (the jars are built by Gradle), so it's only written once.
const fs = require('fs'), path = require('path');

const properties = fs.readFileSync(path.join(__dirname, '..', 'gradle.properties'), 'utf8');
const VERSION = /^version=(.+)$/m.exec(properties)[1].trim();

module.exports = {
	VERSION,
	MC: '26.3',
	DATA_FORMAT: 121, // pack_version.data_major in 26.3's version.json
	// The mod has the trades, the rules and the Hearth Lantern; the data pack doesn't (see tools/build.js).
	DESCRIPTION: 'Put the kettle on and build. Thoughtful recipes, trades and rules that take the grind out of decorating.',
	DATAPACK_DESCRIPTION: 'Put the kettle on and build. Thoughtful recipes that take the grind out of decorating.',
};
