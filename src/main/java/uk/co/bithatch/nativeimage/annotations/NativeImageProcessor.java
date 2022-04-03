package uk.co.bithatch.nativeimage.annotations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.StandardLocation;

import com.google.auto.service.AutoService;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@SupportedAnnotationTypes({ "uk.co.bithatch.nativeimage.annotations.Reflectable",
		"uk.co.bithatch.nativeimage.annotations.Resource", "uk.co.bithatch.nativeimage.annotations.Proxy",
		"uk.co.bithatch.nativeimage.annotations.TypeReflect", "uk.co.bithatch.nativeimage.annotations.Query",
		"uk.co.bithatch.nativeimage.annotations.Invoke", "uk.co.bithatch.nativeimage.annotations.Bundle" })
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class NativeImageProcessor extends AbstractProcessor {
	public static final String RESOURCE_PATH = "META-INF/native-image/native-image-annotations/";
	public static final String PROJECT_OPTION = "project";

	@Override
	public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment) {
		processingEnv.getMessager().printMessage(Kind.NOTE,
				"Processing native annotations in " + roundEnvironment.toString() + " / " + typeElements);

		var gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		var proxies = new JsonArray();
		var resourcesRoot = new JsonObject();
		var reflection = new JsonArray();

		/* Default resources */
		var resourcesIncludes = new JsonArray();
		var resources = new JsonObject();
		resources.add("includes", resourcesIncludes);
		resourcesRoot.add("resources", resources);
        var resourceBundles = new JsonArray();
        resourcesRoot.add("bundles", resourceBundles);

		for (var element : roundEnvironment.getElementsAnnotatedWith(Proxy.class)) {
			addInterfaceToProxies(proxies, toClassName((TypeElement)element));
		}

        for (var element : roundEnvironment.getElementsAnnotatedWith(Bundle.class)) {
            addBundleToBundles(resourceBundles, (TypeElement)element);
        }

		for (var element : roundEnvironment.getElementsAnnotatedWith(Resource.class)) {
			var r = element.getAnnotation(Resource.class);
			if(r.siblings()) {
                resourcesIncludes.add(addPatternObject("\\Q" + element.getEnclosingElement().toString().replace(".", "/") + "/.*\\E"));
			}
            var v = r.value();
            if(v.length == 0) {
                if(!r.siblings())
                    resourcesIncludes.add(addPatternObject("\\Q" + toClassName((TypeElement) element).replace(".", "/") + "/.*\\E"));
            }
            else {
                for (var pattern : v) {
    				resourcesIncludes.add(addPatternObject("\\Q" + pattern + "\\E"));
    			}
            }
		}
		for (var element : roundEnvironment.getElementsAnnotatedWith(Reflectable.class)) {
			if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE)
				addClassToReflection(reflection, (TypeElement) element);
		}

		var filer = processingEnv.getFiler();
		if (reflection.size() > 0) {
			var path = createRelativePath("reflect-config.json");
			try {
				var classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path);
				processingEnv.getMessager().printMessage(Kind.NOTE,
						"writing to: " + StandardLocation.CLASS_OUTPUT + "/" + path);
				try (var w = new PrintWriter(classFile.openOutputStream())) {
					w.println(gson.toJson(reflection));
				}
			} catch (IOException e) {
				throw new IllegalStateException("Could not write.", e);
			}
		}

		if (proxies.size() > 0) {
			var path = createRelativePath("proxy-config.json");
			try {
				var classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path);
				processingEnv.getMessager().printMessage(Kind.NOTE,
						"writing to: " + StandardLocation.CLASS_OUTPUT + "/" + path);
				try (var w = new PrintWriter(classFile.openOutputStream())) {
					w.println(gson.toJson(proxies));
				}
			} catch (IOException e) {
				throw new IllegalStateException("Could not write.", e);
			}
		}

		if (proxies.size() > 0 && resourcesRoot.size() > 0) {
			var path = createRelativePath("resource-config.json");
			try {
				var classFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path);
				processingEnv.getMessager().printMessage(Kind.NOTE,
						"writing to: " + StandardLocation.CLASS_OUTPUT + "/" + path);
				try (var w = new PrintWriter(classFile.openOutputStream())) {
					w.println(gson.toJson(resourcesRoot));
				}
			} catch (IOException e) {
				throw new IllegalStateException("Could not write.", e);
			}
		}

		return true;
	}

	private JsonObject addPatternObject(String pattern) {
		var o = new JsonObject();
		o.addProperty("pattern", pattern);
		return o;
	}

	protected String createRelativePath(String fileName) {
		Map<String, String> options = processingEnv.getOptions();
		String id = options.get(PROJECT_OPTION);
		String relativeName = RESOURCE_PATH;
		if (id != null) {
			relativeName += id.replace('\\', '/') + "/";
		}
		return relativeName + fileName;
	}

	void addInterfaceToProxies(JsonArray array, String clazz) {
		var object = new JsonObject();
		var ifArray = new JsonArray();
		ifArray.add(clazz);
		object.add("interfaces", ifArray);
		array.add(object);
	}

    void addBundleToBundles(JsonArray array, TypeElement el) {
        var object = new JsonObject();
        var ifArray = new JsonArray();
        for(var l : el.getAnnotation(Bundle.class).locales()) {
            ifArray.add(l);   
        }
        object.addProperty("name", toClassName(el));
        object.add("locales", ifArray);
        array.add(object);
    }

	String toClassName(TypeElement el) {
		Element p = el;
		StringBuilder b = new StringBuilder();

		/* Look for the package */
		while (p != null) {
			if (p.getKind() != ElementKind.CLASS && p.getKind() != ElementKind.INTERFACE)
				break;

			TypeElement e = (TypeElement) p;

			b.insert(0, e.getSimpleName().toString());
			if (e.getNestingKind() == NestingKind.TOP_LEVEL)
				b.insert(0, ".");
			if (e.getNestingKind() == NestingKind.MEMBER)
				b.insert(0, "$");

			p = p.getEnclosingElement();
		}

		if (p != null && p.getKind() == ElementKind.PACKAGE)
			b.insert(0, p.toString());

		return b.toString();
	}

	void addMethodClassReflection(JsonObject classObject, ExecutableElement exec) {
		if (!classObject.has("methods")) {
			classObject.add("methods", new JsonArray());
		}
		var arr = classObject.get("methods").getAsJsonArray();
		var m = new JsonObject();
		m.addProperty("name", exec.getSimpleName().toString());
		if (!exec.getParameters().isEmpty()) {
			var types = new JsonArray();
			for (var parm : exec.getParameters()) {
				types.add(parm.asType().toString());
			}
			m.add("parameterTypes", types);
		}
		arr.add(m);
	}

	void addFieldClassReflection(JsonObject classObject, ExecutableElement exec) {
		if (!classObject.has("fields")) {
			classObject.add("fields", new JsonArray());
		}
		var arr = classObject.get("fields").getAsJsonArray();
		var m = new JsonObject();
		m.addProperty("name", exec.getSimpleName().toString());
		arr.add(m);
	}

	void addClassToReflection(JsonArray array, TypeElement element) {
		var reflectable = element.getAnnotation(Reflectable.class);
		var typeReflect = element.getAnnotation(TypeReflect.class);
		var object = new JsonObject();
		object.addProperty("name", toClassName(element));
		array.add(object);
		int cons = 0;
		int pcons = 0;
		int clz = 0;
		int mth = 0;
		for (var el : element.getEnclosedElements()) {
			if (el.getKind() == ElementKind.CONSTRUCTOR) {
				cons++;
				if (el.getModifiers().contains(Modifier.PUBLIC)) {
					pcons++;
				}
				if (el.getAnnotation(Reflectable.class) != null) {
					var ex = (ExecutableElement) el;
					addMethodClassReflection(object, ex);
				}
			} else if (el.getKind() == ElementKind.METHOD) {
				mth++;
				if (el.getAnnotation(Reflectable.class) != null)
					addMethodClassReflection(object, (ExecutableElement) el);
			} else if (el.getKind() == ElementKind.FIELD) {
				mth++;
				if (el.getAnnotation(Reflectable.class) != null)
					addFieldClassReflection(object, (ExecutableElement) el);
			} else if (el.getKind() == ElementKind.CLASS) {
				clz++;
				addClassToReflection(array, (TypeElement) el);
			}
		}

		var query = element.getAnnotation(Query.class);
		if (query != null || reflectable.all() || typeReflect != null) {
			if (reflectable.all() || (typeReflect != null && typeReflect.constructors())
					|| (query != null && (query.all() || query.publicConstructors())))
				object.addProperty("queryAllPublicConstructors", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.constructors())
					|| (query != null && (query.all() || query.declaredConstructors())))
				object.addProperty("queryAllDeclaredConstructors", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.methods())
					|| (query != null && (query.all() || query.publicMethods())))
				object.addProperty("queryAllPublicMethods", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.methods())
					|| (query != null && (query.all() || query.declaredMethods())))
				object.addProperty("queryAllDeclaredMethods", true);
		} else {
			/* Auto */

			/* Needed? */

//			if(pcons > 0) {
//				object.addProperty("queryAllPublicConstructors", true);
//			}
//			if(mth > 0) {
//				object.addProperty("queryAllDeclaredMethods", true);
//			}
		}

		var invoke = element.getAnnotation(Invoke.class);
		if (invoke != null || reflectable.all() || typeReflect != null) {
			if (reflectable.all() || (typeReflect != null && typeReflect.constructors())
					|| (invoke != null && (invoke.all() || invoke.publicConstructors())))
				object.addProperty("allPublicConstructors", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.constructors())
					|| (invoke != null && (invoke.all() || invoke.declaredConstructors())))
				object.addProperty("allDeclaredConstructors", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.methods())
					|| (invoke != null && (invoke.all() || invoke.publicMethods())))
				object.addProperty("allPublicMethods", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.methods())
					|| (invoke != null && (invoke.all() || invoke.declaredMethods())))
				object.addProperty("allDeclaredMethods", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.fields())
					|| (invoke != null && (invoke.all() || invoke.publicFields())))
				object.addProperty("allPublicFields", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.fields())
					|| (invoke != null && (invoke.all() || invoke.declaredFields())))
				object.addProperty("allDeclaredFields", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.classes())
					|| (invoke != null && (invoke.all() || invoke.publicClasses())))
				object.addProperty("allPublicClasses", true);
			if (reflectable.all() || (typeReflect != null && typeReflect.classes())
					|| (invoke != null && (invoke.all() || invoke.declaredClasses())))
				object.addProperty("allDeclaredClasses", true);
		} else {
			/* Auto */
			/* Needed? */

			// if(clz > 0) {
			// object.addProperty("allDeclaredClasses", true);
			// }
		}
	}
}