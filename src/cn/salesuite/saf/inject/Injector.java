/**
 * 
 */
package cn.salesuite.saf.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import cn.salesuite.saf.inject.annotation.InflateLayout;
import cn.salesuite.saf.inject.annotation.InjectExtra;
import cn.salesuite.saf.inject.annotation.InjectResource;
import cn.salesuite.saf.inject.annotation.InjectSupportFragment;
import cn.salesuite.saf.inject.annotation.InjectSystemService;
import cn.salesuite.saf.inject.annotation.InjectView;
import cn.salesuite.saf.inject.annotation.InjectViews;
import cn.salesuite.saf.inject.annotation.OnClick;
import cn.salesuite.saf.inject.annotation.OnItemClick;
import cn.salesuite.saf.inject.annotation.OnLongClick;
/**
 * 可以注入view、resource、systemservice等等<br>
 * 在Activity中使用注解，首先需要在onCreate（）中使用Injector.injectInto(this);<br>
 * 在Dialog中使用注解，首先需要在构造方法中使用Injector.injectInto(this);<br>
 * 在Fragment中使用注解，首先需要在onCreateView（）中使用Injector.injectInto(this,view);
 * @author Tony Shen
 *
 */
public class Injector {

    public static String TAG = "Injector";

    protected final Context context;
    protected final Object target;
    protected final Activity activity;
    protected final View fragmentView;
    protected final Resources resources;
    protected final Class<?> clazz;
    private final Bundle extras;
    
    private static Map<String,View> viewHandlerMap = new HashMap<String,View>();
    
	public enum Finder {
		DIALOG {
			@Override
			public View findById(Object source, int id) {
				return ((Dialog) source).findViewById(id);
			}
		},
		ACTIVITY {
			@Override
			public View findById(Object source, int id) {
				return findViewById(source, id);
			}
		},

		FRAGMENT {
			@Override
			public View findById(Object source, int id) {
				return findViewById(source, id);
			}
		},
		VIEW {
			@Override
			public View findById(Object source, int id) {
				return findViewById(source, id);
			}
		};

		/**
		 * TODO frankswu : 对activity和fragment增加缓存
		 * 
		 * @param source
		 * @param id
		 * @return
		 */
		protected View findViewById(Object source,int id) {
            String key = source.getClass()+":"+id;
			View view = viewHandlerMap.get(key);

			if (view == null) {
				if (source instanceof Activity) {
					view = ((Activity) source).findViewById(id);
				} else {
					view = ((View) source).findViewById(id);
				}
				viewHandlerMap.put(key, view);
			}
			return view;
		}
		
		public abstract View findById(Object source, int id);
	}
    
    public Injector(Context context) {
        this(context, context);
    }
    
    public Injector(Context context, Object target) {
        if (context == null || target == null) {
            throw new IllegalArgumentException("Context/target may not be null");
        }
        this.context = context;
        this.target = target;
        resources = context.getResources();
        if (context instanceof Activity) {
            activity = (Activity) context;
            Intent intent = activity.getIntent();
            if (intent != null) {
                extras = intent.getExtras();
            } else {
                extras = null;
            }
        } else {
            activity = null;
            extras = null;
        }
        fragmentView = null;
        clazz = target.getClass();
    }
    
	public Injector(Dialog dialog) {
        if (dialog == null) {
            throw new IllegalArgumentException("dialog may not be null");
        }
        this.target = dialog;
        resources = dialog.getContext().getResources();
        context = dialog.getContext();
        clazz = target.getClass();
        activity = null;
        extras = null;
        fragmentView = null;
	}
	
	public Injector(Fragment fragment , View v) {
        if (fragment == null || v == null) {
            throw new IllegalArgumentException("fragment/view may not be null");
        }
        fragmentView = v;
        this.target = fragment;
        resources = fragment.getResources();
        context = fragment.getActivity().getApplicationContext();
        clazz = fragment.getClass();
        activity = null;
        extras = null;
	}
	
	public Injector(View view) {
        if (view == null) {
            throw new IllegalArgumentException("view may not be null");
        }
        this.target = view;
        resources = view.getContext().getResources();
        context = view.getContext();
        clazz = target.getClass();
        activity = null;
        extras = null;
        fragmentView = null;
	}

	/**
	 * 在Activity中使用注解
	 * @param context
	 * @return
	 */
	public static Injector injectInto(Context context) {
        return inject(context, context);
    }
	
	/**
	 * 在dialog中使用注解
	 * @param dialog
	 * @return
	 */
	public static Injector injectInto(Dialog dialog) {
        Injector injector = new Injector(dialog);
        injector.injectAll(Finder.DIALOG);
        return injector;
    }
	
	/**
	 * 在fragment中使用注解
	 * @param fragment
	 * @param v
	 * @return
	 */
	public static Injector injectInto(Fragment fragment,View v) {
        Injector injector = new Injector(fragment,v);
        injector.injectAll(Finder.FRAGMENT);
        return injector;
    }

	public static Injector inject(Context context, Object target) {
        Injector injector = new Injector(context, target);
        injector.injectAll(Finder.ACTIVITY);
        return injector;
    }

    public static <T extends ViewGroup> T build(Context context, Class<T> clazz) {
        T view = null;

        InflateLayout inflateLayout = clazz.getAnnotation(InflateLayout.class);
        if (inflateLayout != null) {
            int layoutResId = inflateLayout.id();
            
            try {
				view = clazz.getConstructor(Context.class).newInstance(context);
	            View.inflate(context, layoutResId, view);
	            
	        	Injector injector = new Injector(view);
	            injector.injectAll(Finder.VIEW);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
        }

        return view;
    }
    
	private void injectAll(Finder finder) {
        injectFields(finder);
        bindMethods(finder);
	}

	private void injectFields(Finder finder) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {

            Annotation[] annotations = field.getAnnotations();
            View view = null;
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == InjectView.class) {
                    int id = ((InjectView) annotation).id();
                    view = findViewByAnnotationId(id,field,finder);
                    if (view == null) {
                        throw new InjectException("View not found for member " + field.getName());
                    }
                    injectIntoField(field, view);
                } else if (annotation.annotationType() == InjectViews.class) {
                	String fieldTypeName = field.getType().getName();
					// TODO frankswu add injectViews 
                	if (fieldTypeName.startsWith("[L") || fieldTypeName.startsWith("java.util.List")) {
                        int[] ids = ((InjectViews) annotation).ids();
                        List<View> views = new ArrayList<View>();
                        for (int id : ids) {
                            view = findViewByAnnotationId(id,field,finder);
                            if (view == null) {
                                throw new InjectException("View not found for member " + field.getName()+", and id is " + id);
                            }
                            views.add(view);
    					}
	                    if (fieldTypeName.startsWith("[L")) {
	                    	View[] v = (View[]) Array.newInstance(field.getType().getComponentType(), views.size());
		                    injectListIntoField(field, views.toArray(v));                	
						}
	                    if (fieldTypeName.startsWith("java.util.List")) {
		                    injectIntoField(field, views);                	
						}
					} else {
                        throw new InjectException("The View of InjectViews annotation " + field.getName()+" is not list or array !");
					}
                } else if (annotation.annotationType() == InjectResource.class) {
                    Object ressource = findResource(field.getType(), field, (InjectResource) annotation);
                    injectIntoField(field, ressource);
                } else if (annotation.annotationType() == InjectSystemService.class) {
                	String serviceName = ((InjectSystemService) annotation).value();
                	Object service = context.getSystemService(serviceName);
                    injectIntoField(field, service);
                } else if (annotation.annotationType() == InjectExtra.class) {
                    if (extras != null) {
                        Object value = extras.get(((InjectExtra) annotation).key());
                        injectIntoField(field, value);
                    }
                } else if (annotation.annotationType() == InjectSupportFragment.class) {
                    int id = ((InjectSupportFragment) annotation).id();
                    Fragment fragment = findSupportFragment(field, id);
                    injectIntoField(field, fragment);
                }
            }
        }
	}
	
	/**
	 * 查找fragment
	 * @param field
	 * @param fragmentId
	 * @return
	 */
	private Fragment findSupportFragment(Field field, int fragmentId) {
        if (activity == null) {
            throw new InjectException("Fragment can be injected only in activities (member " + field.getName() + " in "
                    + context.getClass());
        }
        Fragment fragment = null;
        if (activity instanceof FragmentActivity) {
        	fragment = ((FragmentActivity)activity).getSupportFragmentManager().findFragmentById(fragmentId);
        }
        if (fragment == null) {
            throw new InjectException("Fragment not found for member " + field.getName());
        }
        return fragment;
	}
	
	/**
	 * 查找resource，目前暂不支持Animations等 
	 * @param cls
	 * @param field
	 * @param annotation
	 * @return
	 */
	private Object findResource(Class<?> cls, Field field,
			InjectResource annotation) {
        int id = annotation.id();
        if (cls == int.class) {          //从color.xml中获取对应的值
        	return context.getResources().getColor(id);
        } else if (cls == String.class) {//从strings.xml中获取对应的值
            return context.getString(id);
        } else if (Drawable.class.isAssignableFrom(cls)) {//从drawable文件中获取资源
            return resources.getDrawable(id);
        } else if (Bitmap.class.isAssignableFrom(cls)) {
            return BitmapFactory.decodeResource(resources, id);
        } else if (cls.isArray()) {      //从arrays.xml中获取对应的值
        	if (cls.getComponentType() == String.class) {
        		return context.getResources().getStringArray(id);
        	}
        	throw new InjectException("Cannot inject for type " + cls + " (field " + field.getName() + ")");
        }
        else {
            throw new InjectException("Cannot inject for type " + cls + " (field " + field.getName() + ")");
        }
	}
	
	private void injectIntoField(Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new InjectException("Could not inject into field " + field.getName(), e);
        }
	}

	private void injectListIntoField(Field field, Object[] value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new InjectException("Could not inject into field " + field.getName(), e);
        }
	}
	
	private void bindMethods(Finder finder) {
        Method[] methods = clazz.getDeclaredMethods();
        Set<View> modifiedViews = new HashSet<View>();
        for (final Method method : methods) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == OnClick.class) {
                    bindOnClickListener(method, (OnClick) annotation, modifiedViews ,finder);
                } else if (annotation.annotationType() == OnItemClick.class) {
//                  frankswu add OnItemClick
                	bindOnItemClickListener(method, (OnItemClick) annotation, modifiedViews ,finder);
                } else if (annotation.annotationType() == OnLongClick.class) {
//                  frankswu add OnLongClick
                	bindOnLongClickListener(method, (OnLongClick) annotation, modifiedViews ,finder);
                } 
            }
        }
	}
	
	private boolean bindOnLongClickListener(Method method, OnLongClick onLongClick,
			Set<View> modifiedViews, Finder finder) {
		// frankswu add OnLongClick 
        boolean invokeWithView = checkInvokeWithView(method, new Class[]{View.class});
        
        method.setAccessible(true);
        InjectedOnLongClickListener listener = new InjectedOnLongClickListener(target, method, invokeWithView);

        int[] ids = onLongClick.id();
        for (int id : ids) {
            if (id != 0) {
                View view = findView(method, id, finder);
//                boolean modified = modifiedViews.add(view);
//                if (!modified) {
//                    throw new InjectException("View can be bound to methods only once using OnLongClick: "
//                            + method.getName());
//                }
                view.setOnLongClickListener(listener);
            }
        }
        return invokeWithView;
	}

	/**
	 * @param method
	 * @param annotation
	 * @param modifiedViews
	 * @param finder
	 */
	private boolean bindOnItemClickListener(Method method, OnItemClick onItemClick, Set<View> modifiedViews, Finder finder) {
		// frankswu : add OnItemClick 
        boolean invokeWithView = checkInvokeWithView(method, new Class[]{AdapterView.class, View.class, int.class, long.class});
        
        method.setAccessible(true);
        InjectedOnItemClickListener listener = new InjectedOnItemClickListener(target, method, invokeWithView);

        int[] ids = onItemClick.id();
        for (int id : ids) {
            if (id != 0) {
            	AdapterView<?> view = null;
            	try {
                    view = (AdapterView<?>) findView(method, id, finder);
				} catch (Exception e) {
                    throw new InjectException("The view can be cast to AdapterView for using OnItemClick! ");
				}
                
//                boolean modified = modifiedViews.add(view);
//                if (!modified) {
//                    throw new InjectException("View can be bound to methods only once using OnItemClick: " + method.getName());
//                }
                view.setOnItemClickListener(listener);
            }
        }
        return invokeWithView;
		
	}

	private boolean bindOnClickListener(final Method method, OnClick onClick, Set<View> modifiedViews, Finder finder) {
		
        boolean invokeWithView = checkInvokeWithView(method, new Class[]{View.class});
        
        method.setAccessible(true);
        InjectedOnClickListener listener = new InjectedOnClickListener(target, method, invokeWithView);

        int[] ids = onClick.id();
        for (int id : ids) {
            if (id != 0) {
                View view = findView(method, id, finder);
//                boolean modified = modifiedViews.add(view);
//                if (!modified) {
//                    throw new InjectException("View can be bound to methods only once using OnClick: "
//                            + method.getName());
//                }
                view.setOnClickListener(listener);
            }
        }
        return invokeWithView;
    }
	
	private boolean checkInvokeWithView(Method method, Class[] paramterClass) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int paramterNum = paramterClass.length;
        if (parameterTypes.length == 0) {
            return false;
        } else if (parameterTypes.length == paramterNum) {
        	if (paramterClass.length == parameterTypes.length) {
            	for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] == paramterClass[i]) {
                        return true;
                    } else {
                        throw new InjectException("the " + method.getName() + ", the correct paramter type is " + paramterClass[i] +
                                		", but now found paramter type is" + parameterTypes[i]+" !");
                    }
    			}
			} else { 
				return false;
			}
        } else {
            throw new InjectException("Method may have no parameter or the number of parameter is wrong: "
                    + method.getName()+" paramter number [" + paramterNum +  "]is correct,but now is " + parameterTypes.length);
        }
		return false;
	}

	/**
	 * frankswu not find id,default use field name 
	 * @param id
	 * @param field
	 * @param finder
	 * @return
	 */
	private View findViewByAnnotationId(int id, Field field, Finder finder) {
        if(id == 0){
        	id = this.context.getResources().getIdentifier(field.getName(), "id", this.context.getPackageName());
            if (id == 0) {
                throw new InjectException("View not found for member " + field.getName());
            }
        }
        return findView(field, id, finder);
	}

	
	
	private View findView(Member field, int viewId, Finder finder) {
		View view = null;
		switch (finder) {
		case DIALOG:
			view= Finder.DIALOG.findById(target, viewId);
			break;
		case ACTIVITY:
			if (activity == null) {
				throw new InjectException("Views can be injected only in activities (member " + field.getName() + " in " + context.getClass());
			}
			view = finder.findById(activity, viewId);
			if (view == null) {
				throw new InjectException("View not found for member "
						+ field.getName());
			}
			break;
		case FRAGMENT:
			view = Finder.FRAGMENT.findById(fragmentView, viewId);
			break;
		case VIEW:
			view = Finder.VIEW.findById(target, viewId);
			break;
		default:
			break;
		}
		return view;
	}
	
}