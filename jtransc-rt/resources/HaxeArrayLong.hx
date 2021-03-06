import haxe.ds.Vector;
import haxe.Int64;

class HaxeArrayLong extends HaxeArrayBase {
    public var data:Vector<Int64> = null;

    public function new(length:Int) {
        super();
        this.data = new Vector<Int64>(length);
        this.length = length;
        this.desc = "[J";
    }

    public function getTypedArray() return data;

    static public function fromArray(items:Array<Dynamic>) {
        if (items == null) return null;
        var out = new HaxeArrayLong(items.length);
        for (n in 0 ... items.length) out.set(n, items[n]);
        return out;
    }

    inline public function get(index:Int):Int64 {
		checkBounds(index);
        return this.data[index];
    }

    inline public function set(index:Int, value:Int64):Void {
		checkBounds(index);
        this.data[index] = value;
    }

	override public function getDynamic(index:Int):Dynamic {
	    return get(index);
	}

	override public function setDynamic(index:Int, value:Dynamic) {
	    set(index, value);
	}

    public function join(separator:String) {
        var out = '';
        for (n in 0 ... length) {
            if (n != 0) out += separator;
            out += get(n);
        }
        return out;
    }

    public override function clone() return fromArray(this.data.toArray());

    static public function copy(from:HaxeArrayLong, to:HaxeArrayLong, fromPos:Int, toPos:Int, length:Int) {
    	if (from == to && toPos > fromPos) {
			var n = length;
			while (--n >= 0) to.set(toPos + n, from.get(fromPos + n));
    	} else {
	        for (n in 0 ... length) to.set(toPos + n, from.get(fromPos + n));
	    }
    }
}