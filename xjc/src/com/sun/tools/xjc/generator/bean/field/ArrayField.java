/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.tools.xjc.generator.bean.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.generator.bean.ClassOutlineImpl;
import com.sun.tools.xjc.generator.bean.MethodWriter;
import com.sun.tools.xjc.model.CPropertyInfo;

/**
 * Realizes a property as an "indexed property"
 * as specified in the JAXB spec.
 * 
 * <p>
 * We will generate the following set of methods:
 * <pre>
 * T[] getX();
 * T getX( int idx );
 * void setX(T[] values);
 * void setX( int idx, T value );
 * </pre>
 * 
 * We still use List as our back storage.
 * This renderer also handles boxing/unboxing if
 * T is a boxed type.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class ArrayField extends AbstractListField {
    
    class Accessor extends AbstractListField.Accessor {
        protected Accessor( JExpression $target ) {
            super($target);
        }
        
        public void toRawValue(JBlock block, JVar $var) {
            block.assign($var,codeModel.ref(Arrays.class).staticInvoke("asList").arg($target.invoke($getAll)));
        }

        public void fromRawValue(JBlock block, String uniqueName, JExpression $var) {
            block.invoke($target,$setAll).arg($var.invoke("toArray").arg(JExpr.newArray(exposedType,$var.invoke("size"))));
        }
    }
    
    private JMethod $setAll;
    
    private JMethod $getAll;
    
    ArrayField(ClassOutlineImpl context, CPropertyInfo prop) {
        super(context,prop,false);
        generate();
    }
    
    public void generateAccessors() {
        
        MethodWriter writer = outline.createMethodWriter();
        Accessor acc = create(JExpr._this());
        
        JVar $idx,$value; JBlock body;
        JType arrayType = exposedType.array();
        
        // [RESULT] T[] getX() {
        //     if( <var>==null )    return new T[0];
        //     return (T[]) <var>.toArray(new T[<var>.size()]);
        // }
        $getAll = writer.declareMethod( exposedType.array(),"get"+prop.getName(true));
        writer.javadoc().append(prop.javadoc);
        body = $getAll.body();

        body._if( acc.ref(true).eq(JExpr._null()) )._then()
            ._return(JExpr.newArray(exposedType,0));

        if(primitiveType==null) {
            body._return(JExpr.cast(arrayType,
                acc.ref(true).invoke("toArray").arg( JExpr.newArray(implType,acc.ref(true).invoke("size")) )));
        } else {
            // need to copy them manually to unbox values
            // [RESULT]
            // T[] r = new T[<ref>.size()];
            // for( int i=0; i<r.length; i++ )
            //     r[i] = unbox(<ref>.get(i));
            JVar $r = body.decl(exposedType.array(),"r",JExpr.newArray(exposedType, acc.ref(true).invoke("size")));
            JForLoop loop = body._for();
            JVar $i = loop.init(codeModel.INT,"__i",JExpr.lit(0));
            loop.test($i.lt($r.ref("length")));
            loop.update($i.incr());
            loop.body().assign( $r.component($i),
                primitiveType.unwrap(acc.ref(true).invoke("get").arg($i)) );
            body._return($r);
        }

        List<Object> returnTypes = listPossibleTypes(prop);
        writer.javadoc().addReturn().append("array of\n").append(returnTypes);
                        
        // [RESULT]
        // ET getX(int idx) {
        //     if( <var>==null )    throw new IndexOutOfBoundsException();
        //     return unbox(<var>.get(idx));
        // }
        JMethod $get = writer.declareMethod(exposedType,"get"+prop.getName(true));
        $idx = writer.addParameter(codeModel.INT,"idx");

        $get.body()._if(acc.ref(true).eq(JExpr._null()))._then()
            ._throw(JExpr._new(codeModel.ref(IndexOutOfBoundsException.class)));

        writer.javadoc().append(prop.javadoc);
        $get.body()._return(acc.unbox(acc.ref(true).invoke("get").arg($idx) ));

        writer.javadoc().addReturn().append("one of\n").append(returnTypes);

                        
        // [RESULT] int getXLength() {
        //     if( <var>==null )    throw new IndexOutOfBoundsException();
        //     return <ref>.size();
        // }
        JMethod $getLength = writer.declareMethod(codeModel.INT,"get"+prop.getName(true)+"Length");
        $getLength.body()._if(acc.ref(true).eq(JExpr._null()))._then()
                ._return(JExpr.lit(0));
        $getLength.body()._return(acc.ref(true).invoke("size"));
        
                        
        // [RESULT] void setX(ET[] values) {
        //     clear();
        //     int len = values.length;
        //     for( int i=0; i<len; i++ )
        //         <ref>.add(values[i]);
        // }
        $setAll = writer.declareMethod(
            codeModel.VOID,
            "set"+prop.getName(true));
        
        writer.javadoc().append(prop.javadoc);
        
        $value = writer.addParameter(exposedType.array(),"values");
        $setAll.body().invoke(acc.ref(false),"clear");
        JVar $len = $setAll.body().decl(codeModel.INT,"len", $value.ref("length"));
        JForLoop _for = $setAll.body()._for();
        JVar $i = _for.init( codeModel.INT, "i", JExpr.lit(0) );
        _for.test( JOp.lt($i,$len) );
        _for.update( $i.incr() );
        _for.body().invoke(acc.ref(true),"add").arg(castToImplType(acc.box($value.component($i))));

        writer.javadoc().addParam($value)
            .append("allowed objects are\n")
            .append(returnTypes);
                        
        // [RESULT] ET setX(int,ET)
        JMethod $set = writer.declareMethod(
            exposedType,
            "set"+prop.getName(true));
        $idx = writer.addParameter( codeModel.INT, "idx" );
        $value = writer.addParameter( exposedType, "value" );

        writer.javadoc().append(prop.javadoc);
                        
        body = $set.body();
        body._return( acc.unbox(
            acc.ref(true).invoke("set").arg($idx).arg(castToImplType(acc.box($value)))));

        writer.javadoc().addParam($value)
            .append("allowed object is\n")
            .append(returnTypes);
    }
    
    protected JClass getCoreListType() {
        return codeModel.ref(ArrayList.class).narrow(exposedType.boxify());
    }
    
    public Accessor create(JExpression targetObject) {
        return new Accessor(targetObject);
    }
}