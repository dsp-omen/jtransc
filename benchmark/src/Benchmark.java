import com.jtransc.simd.MutableFloat32x4;
import com.jtransc.simd.Float32x4;
import com.jtransc.simd.MutableMatrixFloat32x4x4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipFile;

public class Benchmark {
	interface Task {
		int run();
	}

	static private class Test1 {
	}

	static private class Test2 {
	}

	static public void main(String[] args) {
		System.out.println("Benchmarking:");

		benchmark("plain loops", new Task() {
			@Override
			public int run() {
				int m = 0;
				for (int n = 0; n < 1000000; n++) {
					m += n;
				}
				return m;
			}
		});

		benchmark("call static mult", new Task() {
			@Override
			public int run() {
				int m = 0;
				for (int n = 0; n < 1000000; n++) {
					m += calc(m, n);
				}
				return m;
			}
		});

		benchmark("call instance mult", new Task() {
			@Override
			public int run() {
				int m = 0;
				for (int n = 0; n < 1000000; n++) {
					m += calc(m, n);
				}
				return m;
			}

			private int calc(int a, int b) {
				return (a + b) * (a + b);
			}
		});

		benchmark("call instance div", new Task() {
			@Override
			public int run() {
				int m = 1;
				for (int n = 1; n < 1000000; n++) {
					m += calc(m, n);
				}
				return m;
			}

			private int calc(int a, int b) {
				return (a - b) / (a + b);
			}
		});

		benchmark("instanceof classes", new Task() {
			@Override
			@SuppressWarnings("all")
			public int run() {
				int m = 1;
				int rand = rand(2);
				Object test1 = genObj((rand + 0) % 2);
				Object test2 = genObj((rand + 1) % 2);
				for (int n = 1; n < 1000000; n++) {
					if (test1 instanceof Test1) {
						m += n - 1;
					} else if (test1 instanceof Test2) {
						m += n + 2;
					}

					if (test2 instanceof Test1) {
						m += n - 3;
					} else if (test2 instanceof Test2) {
						m += n + 4;
					}
				}
				return m;
			}

			private int rand(int count) {
				return (int)(System.currentTimeMillis() % (long)count);
			}

			private Object genObj(int index) {
				switch (index) {
					case 0: return new Test1();
					default: return new Test2();
				}
			}
		});


		benchmark("write int[]", new Task() {
			@Override
			public int run() {
				int[] array = new int[1000000];
				for (int n = 0; n < 1000000; n++) {
					array[n] = n * 1000;
				}
				return (int) array[7];
			}
		});

		benchmark("write float[]", new Task() {
			@Override
			public int run() {
				float[] array = new float[1000000];
				for (int n = 0; n < 1000000; n++) {
					array[n] = n * 1000;
				}
				return (int) array[7];
			}
		});

		benchmark("String Builder", new Task() {
			@Override
			public int run() {
				StringBuilder out = new StringBuilder();

				for (int n = 0; n < 100000; n++) {
					out.append(n);
				}

				return (int)out.toString().hashCode();
			}
		});

		benchmark("simd mutable", new Task() {
			@Override
			public int run() {
				MutableFloat32x4 a = new MutableFloat32x4();
				MutableFloat32x4 b = new MutableFloat32x4(2f, 3f, 4f, 5f);

				for (int n = 0; n < 1000000; n++) {
					a.setToAdd(a, b);
				}

				return (int)a.getX() + (int)a.getY() + (int)a.getZ() + (int)a.getW();
			}
		});

		benchmark("simd immutable", new Task() {
			@Override
			public int run() {
				Float32x4 a = Float32x4.create(0f, 0f, 0f, 0f);
				Float32x4 b = Float32x4.create(2f, 3f, 4f, 5f);

				for (int n = 0; n < 1000000; n++) {
					a = Float32x4.add(a, b);
				}

				return (int)Float32x4.getX(a) + (int)Float32x4.getY(a) + (int)Float32x4.getZ(a) + (int)Float32x4.getW(a);
			}
		});

		benchmark("simd mutable matrix mult", new Task() {
			@Override
			public int run() {
				MutableMatrixFloat32x4x4 a = new MutableMatrixFloat32x4x4().setTo(
					1f, 9f, 1f, 7f,
					3f, 2f, 4f, 5f,
					3f, 7f, 3f, 3f,
					3f, 8f, 4f, 4f
				);
				MutableMatrixFloat32x4x4 b = new MutableMatrixFloat32x4x4().setTo(
					2f, 3f, 4f, 5f,
					2f, 3f, 4f, 5f,
					2f, 3f, 4f, 5f,
					2f, 3f, 4f, 5f
				);

				for (int n = 0; n < 100000; n++) {
					a.setToMul44(a, b);
				}

				return (int)a.getSumAll();
			}
		});

		char[] hexDataChar = new char[]{
			0x50, 0x4B, 0x03, 0x04, 0x0A, 0x03, 0x00, 0x00, 0x00, 0x00, 0x49, 0x9E, 0x74, 0x48, 0xA3, 0x1C,
			0x29, 0x1C, 0x0C, 0x00, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x68, 0x65,
			0x6C, 0x6C, 0x6F, 0x2E, 0x74, 0x78, 0x74, 0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20, 0x57, 0x6F, 0x72,
			0x6C, 0x64, 0x21, 0x50, 0x4B, 0x03, 0x04, 0x14, 0x03, 0x00, 0x00, 0x08, 0x00, 0x35, 0xB5, 0x74,
			0x48, 0xAA, 0xC0, 0x69, 0x3A, 0x1D, 0x00, 0x00, 0x00, 0x38, 0x07, 0x00, 0x00, 0x0A, 0x00, 0x00,
			0x00, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x32, 0x2E, 0x74, 0x78, 0x74, 0xF3, 0x48, 0xCD, 0xC9, 0xC9,
			0x57, 0x08, 0xCF, 0x2F, 0xCA, 0x49, 0x51, 0x1C, 0x65, 0x8F, 0xB2, 0x47, 0xD9, 0xA3, 0xEC, 0x51,
			0xF6, 0x28, 0x7B, 0x94, 0x8D, 0x9F, 0x0D, 0x00, 0x50, 0x4B, 0x01, 0x02, 0x3F, 0x03, 0x0A, 0x03,
			0x00, 0x00, 0x00, 0x00, 0x49, 0x9E, 0x74, 0x48, 0xA3, 0x1C, 0x29, 0x1C, 0x0C, 0x00, 0x00, 0x00,
			0x0C, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x80,
			0xA4, 0x81, 0x00, 0x00, 0x00, 0x00, 0x68, 0x65, 0x6C, 0x6C, 0x6F, 0x2E, 0x74, 0x78, 0x74, 0x50,
			0x4B, 0x01, 0x02, 0x3F, 0x03, 0x14, 0x03, 0x00, 0x00, 0x08, 0x00, 0x35, 0xB5, 0x74, 0x48, 0xAA,
			0xC0, 0x69, 0x3A, 0x1D, 0x00, 0x00, 0x00, 0x38, 0x07, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x20, 0x80, 0xA4, 0x81, 0x33, 0x00, 0x00, 0x00, 0x68, 0x65, 0x6C,
			0x6C, 0x6F, 0x32, 0x2E, 0x74, 0x78, 0x74, 0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x02,
			0x00, 0x02, 0x00, 0x6F, 0x00, 0x00, 0x00, 0x78, 0x00, 0x00, 0x00, 0x00, 0x00
		};
		byte[] hexData = new byte[hexDataChar.length];
		for (int n = 0; n < hexDataChar.length; n++) hexData[n] = (byte) hexDataChar[n];

		//benchmark("decompress zlib", new Task() {
		//	@Override
		//	public int run() {
		//
		//		//new ZipFile("memory://hex")
		//		//com.jtransc.compression.JTranscZlib.inflate(hexData, 1848);
		//		return -1;
		//		//try {
		//		//	InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(hexData));
		//		//	ByteArrayOutputStream out = new ByteArrayOutputStream();
		//		//	com.jtransc.io.JTranscIoTools.copy(is, out);
		//		//	return (int) out.size();
		//		//} catch (Throwable t) {
		//		//	t.printStackTrace();
		//		//	return 0;
		//		//}
		//	}
		//});

		//benchmark("compress zlib", new Task() {
		//	@Override
		//	public int run() {
		//		try {
		//			Random random = new Random(0L);
		//			byte[] bytes = new byte[16 * 1024];
		//			for (int n = 0; n < bytes.length; n++) bytes[n] = (byte) random.nextInt();
		//
		//
		//			DeflaterInputStream is = new DeflaterInputStream(new ByteArrayInputStream(bytes));
		//			ByteArrayOutputStream out = new ByteArrayOutputStream();
		//			com.jtransc.io.JTranscIoTools.copy(is, out);
		//			return (int) out.size();
		//		} catch (Throwable t) {
		//			t.printStackTrace();
		//			return 0;
		//		}
		//	}
		//});
	}

	static private void benchmark(String name, Task run) {
		System.out.print(name + "...");

		long t1 = System.currentTimeMillis();
		for (int n = 0; n < 10; n++) run.run(); // warming up
		long t2 = System.currentTimeMillis();
		for (int n = 0; n < 10; n++) run.run();
		long t3 = System.currentTimeMillis();
		//System.out.println("( " + (t2 - t1) + " ) :: ( " + (t3 - t2) + " )");
		System.out.println(t3 - t2);
	}

	static public int calc(int a, int b) {
		return (a + b) * (a + b);
	}
}
