// What the generator and the build both need to know about the pack, in one place.
module.exports = {
	VERSION: '0.1.0',
	MC: '26.3',
	DATA_FORMAT: 121, // pack_version.data_major in 26.3's version.json
	// The mod has the trades; the data pack doesn't (see tools/build.js).
	DESCRIPTION: 'Put the kettle on and build. Thoughtful recipes and trades that take the grind out of decorating.',
	DATAPACK_DESCRIPTION: 'Put the kettle on and build. Thoughtful recipes that take the grind out of decorating.',
};
