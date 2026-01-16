package game.engine.core.persistence.processor;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Delta字段注解处理器 - 编译期自动生成字段索引常量。
 * 
 * 功能：
 * 1. 扫描所有带 @DeltaColumn 注解的字段
 * 2. 为每个 DeltaEntity 子类生成 XXXFields.java
 * 3. 自动分配字段索引，避免手动维护
 * 
 * 生成示例：
 * 对于 Player 实体，生成 PlayerFields.java：
 * <pre>
 * public final class PlayerFields {
 *     public static final int NICKNAME = 0;
 *     public static final int LEVEL = 1;
 * }
 * </pre>
 */
@SupportedAnnotationTypes("game.engine.core.persistence.annotation.DeltaColumn")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class DeltaFieldProcessor extends AbstractProcessor {
    
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || annotations.isEmpty()) {
            return false;
        }
        
        try {
            // 按类分组收集所有 @DeltaColumn 字段
            Map<TypeElement, List<VariableElement>> fieldsMap = collectDeltaFields(roundEnv);
            
            // 为每个实体类生成 XXXFields.java
            for (Map.Entry<TypeElement, List<VariableElement>> entry : fieldsMap.entrySet()) {
                generateFieldsClass(entry.getKey(), entry.getValue());
            }
            
            return true;
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "DeltaFieldProcessor failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 收集所有 @DeltaColumn 字段，按类分组
     */
    private Map<TypeElement, List<VariableElement>> collectDeltaFields(RoundEnvironment roundEnv) {
        Map<TypeElement, List<VariableElement>> fieldsMap = new LinkedHashMap<>();
        
        for (Element element : roundEnv.getElementsAnnotatedWith(DeltaColumn.class)) {
            if (element.getKind() != ElementKind.FIELD) {
                messager.printMessage(Diagnostic.Kind.ERROR, 
                    "@DeltaColumn can only be applied to fields", element);
                continue;
            }
            
            VariableElement field = (VariableElement) element;
            TypeElement classElement = (TypeElement) field.getEnclosingElement();
            
            // 验证该类是否继承 DeltaEntity
            if (!isDeltaEntitySubclass(classElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "@DeltaColumn can only be used in DeltaEntity subclasses", classElement);
                continue;
            }
            
            fieldsMap.computeIfAbsent(classElement, k -> new ArrayList<>()).add(field);
        }
        
        return fieldsMap;
    }
    
    /**
     * 检查类是否继承 DeltaEntity
     */
    private boolean isDeltaEntitySubclass(TypeElement classElement) {
        TypeMirror deltaEntityType = elementUtils.getTypeElement(
            "game.engine.core.sync.DeltaEntity").asType();
        
        TypeMirror superclass = classElement.getSuperclass();
        while (superclass != null && superclass.getKind() != javax.lang.model.type.TypeKind.NONE) {
            if (typeUtils.isSameType(superclass, deltaEntityType)) {
                return true;
            }
            Element element = typeUtils.asElement(superclass);
            if (element instanceof TypeElement) {
                superclass = ((TypeElement) element).getSuperclass();
            } else {
                break;
            }
        }
        return false;
    }
    
    /**
     * 生成 XXXFields.java 类
     */
    private void generateFieldsClass(TypeElement classElement, List<VariableElement> fields) throws IOException {
        String packageName = elementUtils.getPackageOf(classElement).getQualifiedName().toString();
        String className = classElement.getSimpleName().toString();
        String fieldsClassName = className + "Fields";
        
        // 按字段名排序，确保生成顺序稳定
        List<VariableElement> sortedFields = fields.stream()
            .sorted(Comparator.comparing(f -> f.getSimpleName().toString()))
            .collect(Collectors.toList());
        
        // 创建源文件
        JavaFileObject fileObject = filer.createSourceFile(packageName + "." + fieldsClassName);
        
        try (PrintWriter writer = new PrintWriter(fileObject.openWriter())) {
            // 包声明
            writer.println("package " + packageName + ";");
            writer.println();
            
            // 类注释
            writer.println("/**");
            writer.println(" * " + className + " 的字段索引常量类。");
            writer.println(" * ");
            writer.println(" * <p>由 DeltaFieldProcessor 自动生成，请勿手动修改。");
            writer.println(" * <p>生成时间: " + new java.util.Date());
            writer.println(" */");
            
            // 类声明
            writer.println("public final class " + fieldsClassName + " {");
            writer.println();
            
            // 生成字段常量
            int index = 0;
            for (VariableElement field : sortedFields) {
                String fieldName = field.getSimpleName().toString();
                String constantName = toConstantName(fieldName);
                DeltaColumn annotation = field.getAnnotation(DeltaColumn.class);
                String dbColumnName = annotation.name();
                
                writer.println("    /**");
                writer.println("     * 字段: " + fieldName);
                writer.println("     * 数据库列: " + dbColumnName);
                writer.println("     */");
                writer.println("    public static final int " + constantName + " = " + index + ";");
                writer.println();
                
                index++;
            }
            
            // 私有构造函数
            writer.println("    /** 工具类，禁止实例化 */");
            writer.println("    private " + fieldsClassName + "() {");
            writer.println("        throw new UnsupportedOperationException(\"Utility class\");");
            writer.println("    }");
            
            writer.println("}");
        }
        
        messager.printMessage(Diagnostic.Kind.NOTE, 
            "Generated " + fieldsClassName + " with " + sortedFields.size() + " fields");
    }
    
    /**
     * 将字段名转换为常量名（驼峰转下划线大写）
     */
    private String toConstantName(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(c);
            } else {
                result.append(Character.toUpperCase(c));
            }
        }
        return result.toString();
    }
}
