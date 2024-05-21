package uk.co.bithatch.nativeimage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.SOURCE)
public @interface Query {
	boolean all() default false;
	
	boolean publicConstructors() default false;

	boolean declaredConstructors() default false;

	boolean publicMethods() default false;

	boolean declaredMethods() default false;
}
