function __createJavaArrayBaseType() {
	var ARRAY = function() {
	};

	__extends(ARRAY.prototype, _global.jtranscClasses["java.lang.Object"].prototype)

	return ARRAY;
}

function __createJavaArrayType(desc, type) {
	var ARRAY = function(size) {
		this.desc = desc;
		this.data = new type(size);
		this.length = size;

		//console.log('Created array instance: [' + desc + ":" + type.name + ":" + size + "]");
	};

	ARRAY.prototype.get = function(index) {
    	return this.data[index];
    };

	ARRAY.prototype.set = function(index, value) {
    	this.data[index] = value;
    };

	ARRAY.prototype.toArray = function() {
    	var out = new Array(this.length);
    	for (var n = 0; n < out.length; n++) out[n] = this.get(n);
    	return out;
    };

    ARRAY.prototype.clone = function() {
    	var out = new ARRAY(this.length);
    	out.data.set(this.data);
        return out;
	};

	ARRAY.prototype["clone()Ljava/lang/Object;"] = ARRAY.prototype.clone;

	__extends(ARRAY.prototype, JA_0);

	return ARRAY;
}

function __createGenericArrayType() {
	var ARRAY = function(size, desc) {
		this.desc = desc;
		this.data = new Array(size);
		this.length = size;
		for (var n = 0; n < size; n++) this.data[n] = null;
	};

	ARRAY.fromArray = function(array, desc) {
		var out = new JA_L(array.length, desc);
		for (var n = 0; n < out.length; n++) out.set(array[n]);
		return out;
	};

	ARRAY.prototype.get = function(index) {
		return this.data[index];
	};

	ARRAY.prototype.set = function(index, value) {
		this.data[index] = value;
	};

	ARRAY.prototype.clone = function() {
		var out = new JA_L(this.length, this.desc);
		for (var n = 0; n < this.length; n++) out.set(n, this.get(n));
		return out;
	};

	ARRAY.prototype.toArray = function() {
		return this.data;
	};

	ARRAY.prototype["clone()Ljava/lang/Object;"] = ARRAY.prototype.clone;

	__extends(ARRAY.prototype, JA_0);

	return ARRAY;
}

var JA_0;
var JA_Z;
var JA_B;
var JA_C;
var JA_S;
var JA_I;
var JA_F;
var JA_D;
var JA_L;


function __createJavaArrays() {
	JA_0 = __createJavaArrayBaseType();
	JA_Z = __createJavaArrayType('[Z', Int8Array);    // Bool Array
	JA_B = __createJavaArrayType('[B', Int8Array);    // Byte Array
	JA_C = __createJavaArrayType('[C', Uint16Array);  // Character Array
	JA_S = __createJavaArrayType('[S', Int16Array);   // Short Array
	JA_I = __createJavaArrayType('[I', Int32Array);   // Int Array
	JA_F = __createJavaArrayType('[F', Float32Array); // Float Array
	JA_D = __createJavaArrayType('[D', Float64Array); // Double Array
	JA_L =__createGenericArrayType(); // Generic Array
}
