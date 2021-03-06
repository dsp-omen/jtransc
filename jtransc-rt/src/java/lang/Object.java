/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import com.jtransc.annotation.JTranscAddFile;
import com.jtransc.annotation.JTranscKeep;
import com.jtransc.annotation.JTranscMethodBody;
import com.jtransc.annotation.JTranscReferenceClass;
import com.jtransc.annotation.haxe.HaxeAddFilesTemplate;
import com.jtransc.annotation.haxe.HaxeAddMembers;
import com.jtransc.annotation.haxe.HaxeAddSubtarget;
import com.jtransc.annotation.haxe.HaxeMethodBody;

import java.lang.reflect.Field;

@HaxeAddMembers({
	"static public var __LAST_ID__ = 0;",
	"public var __ID__ = __LAST_ID__++;",
})
@HaxeAddFilesTemplate({
	"N.hx",
	//"hx/N.hx",
	"R.hx",
	"HaxePolyfills.hx",
	"HaxeNatives.hx",
	"HaxeDynamicLoad.hx",
	"HaxeFfiLibrary.hx",
	"HaxeIO.hx",
	"HaxeNativeWrapper.hx",
	"Float32.hx", "Float64.hx",
	"HaxeArrayBase.hx", "HaxeArrayBool.hx", "HaxeArrayByte.hx", "HaxeArrayShort.hx", "HaxeArrayChar.hx", "HaxeArrayInt.hx", "HaxeArrayFloat.hx", "HaxeArrayDouble.hx", "HaxeArrayLong.hx", "HaxeArrayAny.hx",
	"JA_0.hx", "JA_B.hx", "JA_C.hx", "JA_D.hx", "JA_F.hx", "JA_I.hx", "JA_J.hx", "JA_L.hx", "JA_S.hx", "JA_Z.hx"
})
@HaxeAddSubtarget(name = "js", alias = {"default", "javascript"}, cmdSwitch = "-js", singleFile = true, interpreter = "node", extension = "js")
@HaxeAddSubtarget(name = "cpp", alias = {"c", "c++"}, cmdSwitch = "-cpp", singleFile = true, interpreter = "", extension = "exe")
@HaxeAddSubtarget(name = "swf", alias = {"flash", "as3"}, cmdSwitch = "-swf", singleFile = true, interpreter = "", extension = "swf")
@HaxeAddSubtarget(name = "neko", cmdSwitch = "-neko", singleFile = true, interpreter = "neko", extension = "n")
@HaxeAddSubtarget(name = "php", cmdSwitch = "-php", singleFile = false, interpreter = "php", extension = "php", interpreterSuffix = "/index.php")
@HaxeAddSubtarget(name = "cs", cmdSwitch = "-cs", singleFile = true, interpreter = "", extension = "exe")
@HaxeAddSubtarget(name = "java", cmdSwitch = "-java", singleFile = true, interpreter = "java -jar", extension = "jar")
@HaxeAddSubtarget(name = "python", cmdSwitch = "-python", singleFile = true, interpreter = "python", extension = "py")
@JTranscAddFile(target = "js", priority = -999999999, process = true, prependAppend = "js/Wrapper.js")
@JTranscAddFile(target = "js", priority = -1007, process = true, prepend = "js/ArrayPolyfill.js")
@JTranscAddFile(target = "js", priority = -1006, process = true, prepend = "js/StringPolyfill.js")
@JTranscAddFile(target = "js", priority = -1005, process = true, prepend = "js/MathPolyfill.js")
@JTranscAddFile(target = "js", priority = -1003, process = true, prepend = "js/Arrays.js")
@JTranscAddFile(target = "js", priority = -1002, process = true, prepend = "js/N.js")
@JTranscAddFile(target = "js", priority = -1001, process = true, prepend = "js/R.js")
@JTranscAddFile(target = "js", priority = -1000, process = true, prependAppend = "js/Runtime.js")
public class Object {
	@JTranscMethodBody(target = "js", value = "this.$JS$__id = $JS$__lastId++;")
	public Object() {
	}

	@JTranscKeep
	public boolean equals(Object obj) {
		return (this == obj);
	}

	@HaxeMethodBody("return HaxeNatives.getClass(this);")
	@JTranscMethodBody(target = "js", value = "return R.getClass(this);")
	@JTranscMethodBody(target = "cpp", value = "return {% SMETHOD java.lang.Class:forName0 %}(N::str(TYPE_TABLE::TABLE[this->__INSTANCE_CLASS_ID].tname));")
	native public final Class<?> getClass();

	@JTranscKeep
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@JTranscKeep
	@JTranscMethodBody(target = "js", value = "return N.clone(this);")
	protected Object clone() throws CloneNotSupportedException {
		// @TODO: This is slow! We could override this at code gen knowing all the fields and with generated code to generate them.
		try {
			Class<?> clazz = this.getClass();
			Object newObject = clazz.newInstance();
			for (Field field : clazz.getDeclaredFields()) {
				field.set(newObject, field.get(this));
			}
			return newObject;
		} catch (Throwable e) {
			throw new CloneNotSupportedException(e.toString());
		}
	}

	@JTranscKeep
	//@JTranscMethodBody(target = "js", value = "return N.str('Object');")
	public String toString() {
		return getClass().getName() + "@" + Integer.toHexString(this.hashCode());
	}

	public final void notify() {
	}

	public final void notifyAll() {
	}

	public final void wait(long timeout) throws InterruptedException {
	}

	public final void wait(long timeout, int nanos) throws InterruptedException {
		wait(timeout);
	}

	public final void wait() throws InterruptedException {
		wait(0);
	}

	protected void finalize() throws Throwable {
	}
}
