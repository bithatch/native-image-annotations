package uk.co.bithatch.nativeimage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(OtherReflectables.class)
public @interface OtherReflectable {
	Class<?> value();
	
	boolean all() default false;
	
	Invoke invoke() default @Invoke;
	
	Query query() default @Query;
	
	TypeReflect typeReflect() default @TypeReflect;
}
