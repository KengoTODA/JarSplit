package main;

import gnu.trove.set.hash.THashSet;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import split.*;

public class Main {
	private static byte[] getByte(InputStream iStream) throws IOException{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[16384];
		while ((nRead = iStream.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}
		buffer.flush();
		return buffer.toByteArray();
	}

	private static Set<String> getFileNames(String jarName) throws IOException{
        Set<String> ret = new THashSet<String>();
        JarFile f = new JarFile(new File(jarName));
        Enumeration<? extends JarEntry> en = f.entries();
        while(en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            String name = e.getName();
            if(! e.isDirectory()){
                ret.add(name.replace(".class", "").replace("/", "."));
            }
        }
        f.close();
        return ret;
    }

    private static List<Set<String>> union(List<Set<String>> x){
        List<Set<String>> ret = new ArrayList<Set<String>>();
        Set<String> temp = new THashSet<String>();
        for(Set<String> i : x){
            temp.addAll(i);
        }
        ret.add(temp);
        return ret;
    }

	public static void main(String[] args) throws IOException {
		if(args.length != 1){
        	throw new RuntimeException(".jar file name is required.");
        }
    	// e.g. cfm.jar or /home/USERNAME/Downloads/cfm.jar
    	String jarName = args[0];
        System.out.println("target jar: " + jarName);
        
		long start = System.currentTimeMillis();

		Set<String> fileNames0 = getFileNames(jarName);
		MyDB db = new MyDB(fileNames0);
		MyClassVisitor cv = new MyClassVisitor(Opcodes.ASM4, db);
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        Set<String> fileNames = new THashSet<String>();
		JarFile f = new JarFile(new File(jarName));
		Enumeration<? extends JarEntry> en = f.entries();
		while(en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            String name = e.getName();
            if(! e.isDirectory()){
            	// System.out.println(" ファイル名: [" + name + "]");
            	files.put(name, getByte(f.getInputStream(e)));
            	
                if(name.endsWith(".class")) {
                    ClassReader cr = new ClassReader(f.getInputStream(e));
                    cr.accept(cv, 0);
                }
            }
        }
        f.close();
        
        long end_read = System.currentTimeMillis();
        System.err.println("read all .class time: " + (end_read- start) + "[ms]");

        List<Set<String>> modules = new Spliter().split(db.getDependency(), db.getSuper2Subs(), 5);
        modules = union(modules);
        
        long end_split = System.currentTimeMillis();
        System.err.println("split time: " + (end_split- end_read) + "[ms]");
        
        Set<String> notContain = new THashSet<String>();
        for(int i=0;i<modules.size();i++){
			System.out.println("number: " + i);
	        final File target = new File("jarsample" + i + ".jar");
	        final JarOutputStream jarOutStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target)));
			try {
				/* create .class files */
				for(String className : modules.get(i)) {
					String fileName = className.replace(".", "/") + ".class";
					System.out.println(" ファイル名: [" + fileName + "]");
					if(files.containsKey(fileName)){
						final JarEntry entry = new JarEntry(fileName);
						jarOutStream.putNextEntry(entry);
						jarOutStream.write(files.get(fileName));
						jarOutStream.closeEntry();
						fileNames.remove(fileName);
					}else{
						notContain.add(fileName);
					}
				}
				jarOutStream.finish();
			} finally {
				jarOutStream.close();
			}
		}
        System.out.println("# un used files " + fileNames.size());
        System.out.println("# notContain files " + notContain.size());
        for(String s : notContain){
        	System.out.println(s);
        }
        long end = System.currentTimeMillis();
        System.err.println("write .class time: " + (end - end_split) + "[ms]");
        System.err.println("total time: " + (end - start) + "[ms]");
	}
}