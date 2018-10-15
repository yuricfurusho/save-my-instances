package yuricfurusho;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

public class saveInstancesClass extends AnAction {
    private PsiElementFactory mElementFactory;
    private StringBuilder mInfoBuilder;
    private Project mProject;
    private Editor mEditor;
    private Document mDocument;
    private int mOffset;
    private PsiElement mElementAtCarret;
    private PsiClass mPsiClass;
    private List<PsiField> psiClassFields;
    private PsiExpressionStatement mSuperOnCreateStatement;
    private PsiCodeBlock mPsiIfStatementCodeBlock;
    private PsiElement ifStatementPasted;
    private PsiMethod mOnSaveInstanceStateMethod;
    private PsiCodeBlock mPsiOnSaveInstanceCodeBlock;

    @Override
    public void update(AnActionEvent e) {
        final Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(project != null && editor != null && psiFile != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (!init(e)) return;

        /*TODO : get fields from all activity and fragment classes*/
        /* TODO: get/create onCreate Method for each acitivty and fragment class */
        /* um for each de cada classe e roda o resto dentro desse for each*/

        psiClassFields = getFilteredClassFields(mPsiClass); // TODO pode ser nulo ou vazio, tratar tambem
        mPsiIfStatementCodeBlock = getIfBlockStatement(mPsiClass);

        loadInstances();
        saveInstances();

        // Log
//        for (PsiField psiField : psiClassFields) {
//            mInfoBuilder.append(psiField.getName()).append("\n");
//        }
//        Messages.showMessageDialog(e.getProject(), mInfoBuilder.toString(), "PSI Info", null);
    }

    private void saveInstances() {
        /*7 Criar método saveInstance caso não exista*/
        mOnSaveInstanceStateMethod = (PsiMethod) mElementFactory.createMethodFromText("@Override public void onSaveInstanceState(@NonNull Bundle outState) {super.onSaveInstanceState(outState);}", null);
        mPsiOnSaveInstanceCodeBlock = PsiTreeUtil.findChildOfType(mOnSaveInstanceStateMethod, PsiCodeBlock.class);



        /*8 Inserir um save statement para cada field antes do super mesmo*/
        for (PsiField psiField : psiClassFields) {
            String fieldTypeSave = psiField.getTypeElement().getText();
            String fieldNameSave = psiField.getNameIdentifier().getText();
            String saveStatementText = "outState.putSerializable(" + fieldTypeSave + ".class.getSimpleName(), " + fieldNameSave + ");"; // TODO: 13/08/2018 check if it is a simple object, not a serializable and treat diferrent.
            PsiExpressionStatement psiExpressionStatementSave = (PsiExpressionStatement) mElementFactory.createStatementFromText(saveStatementText, null);

            WriteCommandAction.runWriteCommandAction(mProject, new Runnable() {
                @Override
                public void run() {
                    mPsiOnSaveInstanceCodeBlock.addBefore(psiExpressionStatementSave, mPsiOnSaveInstanceCodeBlock.getLastChild());
                }
            });
        }


        WriteCommandAction.runWriteCommandAction(mProject, new Runnable() {
            @Override
            public void run() {
                mPsiClass.add(mOnSaveInstanceStateMethod);

            }
        });
    }

    private void loadInstances() {
        /**6 - Adicionar um statement de load após o super para cada field dentro do block do if*/

        for (PsiField psiField : psiClassFields) {
            PsiExpressionStatement psiExpressionStatement = (PsiExpressionStatement) mElementFactory.createStatementFromText(getLoadStatementText(psiField), null);

            WriteCommandAction.runWriteCommandAction(mProject, new Runnable() {
                @Override
                public void run() {
                    PsiElement psiElement = mPsiIfStatementCodeBlock.add(psiExpressionStatement);
                }
            });
        }
    }

    private PsiCodeBlock getIfBlockStatement(PsiClass mPsiClass){
        mSuperOnCreateStatement = getSuperOnCreate(mPsiClass);


        PsiIfStatement ifStatement = (PsiIfStatement) mElementFactory.createStatementFromText("if (savedInstanceState != null) {}", null);


        /*5 Inserir ifStatement após o super statement*/
        /* TODO verificar se já não existe esse if statement */
        WriteCommandAction.runWriteCommandAction(mProject, new Runnable() {
            @Override
            public void run() {
                ifStatementPasted = getOnCreateBlock(mPsiClass).addBefore(ifStatement, mSuperOnCreateStatement.getNextSibling());
            }
        });

        PsiBlockStatement psiBlockStatement = PsiTreeUtil.findChildOfType(ifStatementPasted, PsiBlockStatement.class);
        return mPsiIfStatementCodeBlock = PsiTreeUtil.findChildOfType(psiBlockStatement, PsiCodeBlock.class);
    }

    private PsiExpressionStatement getSuperOnCreate(PsiClass psiClass) {
        PsiCodeBlock psiOnCreateCodeBlock = null;
        try {
            psiOnCreateCodeBlock = getOnCreateBlock(mPsiClass);
        } catch (Exception e1) {
            e1.printStackTrace();
        }


        final PsiExpressionStatement[] superOnCreateStatement = new PsiExpressionStatement[1];

        psiOnCreateCodeBlock.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitExpressionStatement(PsiExpressionStatement statement) {
                super.visitExpressionStatement(statement);
                if ("super.onCreate(savedInstanceState);".equalsIgnoreCase(statement.getText()))
                    superOnCreateStatement[0] = statement;
            }
        });
        return superOnCreateStatement[0];
    }

    private PsiCodeBlock getOnCreateBlock(PsiClass psiClass) {
        PsiMethod firstCreateMethod = psiClass.findMethodsByName("onCreate", false)[0]; // TODO substitute for method signature linked to android sdk, so future changes to signature do not affect this. Also to get better precision
        if (firstCreateMethod == null) firstCreateMethod = addOnCreate(psiClass);
        PsiCodeBlock psiOnCreateCodeBlock = PsiTreeUtil.findChildOfType(firstCreateMethod, PsiCodeBlock.class);
        return psiOnCreateCodeBlock;
    }

    private List<PsiField> getFilteredClassFields(PsiClass psiClass) {
        return getFilteredClassFields(psiClass, true);
    }

    private List<PsiField> getFilteredClassFields(PsiClass psiClass, boolean excludeConstants) {
        List<PsiField> psiFields = new ArrayList<PsiField>();

        psiClass.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                if (excludeConstants && field.getFirstChild().getText().contains("final")) return;

                psiFields.add(field);
            }
        });

        return psiFields;
    }

    private String getLoadStatementText(PsiField psiField) {
        String fieldType = psiField.getTypeElement().getText();
        String fieldName = psiField.getNameIdentifier().getText();

        String loadingMethodName = getLoadingMethodName(fieldType);
        if (loadingMethodName != null)
            return fieldName + " = savedInstanceState." + loadingMethodName + "(\"" + fieldName + "\");";
        return fieldName + " = (" + fieldType + ") savedInstanceState.getSerializable(" + fieldType + ".class.getSimpleName());";

    }

    private String getLoadingMethodName(String fieldTypeLoad) {
        // UPDATED: 14/08/2018
        if ("<T extends Parcelable> ArrayList<T>".equalsIgnoreCase(fieldTypeLoad)) return "getParcelableArrayList";
        if ("<T extends Parcelable> SparseArray<T>".equalsIgnoreCase(fieldTypeLoad)) return "getSparseParcelableArray";
        if ("<T extends Parcelable> T".equalsIgnoreCase(fieldTypeLoad)) return "getParcelable";
        if ("ArrayList<CharSequence>".equalsIgnoreCase(fieldTypeLoad)) return "getCharSequenceArrayList";
        if ("ArrayList<Integer>".equalsIgnoreCase(fieldTypeLoad)) return "getIntegerArrayList";
        if ("ArrayList<String>".equalsIgnoreCase(fieldTypeLoad)) return "getStringArrayList";
        if ("boolean".equalsIgnoreCase(fieldTypeLoad)) return "getBoolean";
        if ("Boolean".equalsIgnoreCase(fieldTypeLoad)) return "getBoolean"; // Wrapper class
        if ("boolean[]".equalsIgnoreCase(fieldTypeLoad)) return "getBooleanArray";
        if ("Bundle".equalsIgnoreCase(fieldTypeLoad)) return "getBundle";
        if ("byte".equalsIgnoreCase(fieldTypeLoad)) return "getByte";
        if ("Byte".equalsIgnoreCase(fieldTypeLoad)) return "getByte"; // Wrapper class
        if ("byte[]".equalsIgnoreCase(fieldTypeLoad)) return "getByteArray";
        if ("char".equalsIgnoreCase(fieldTypeLoad)) return "getChar";
        if ("Character".equalsIgnoreCase(fieldTypeLoad)) return "getChar"; // Wrapper class
        if ("char[]".equalsIgnoreCase(fieldTypeLoad)) return "getCharArray";
        if ("CharSequence".equalsIgnoreCase(fieldTypeLoad)) return "getCharSequence";
        if ("CharSequence[]".equalsIgnoreCase(fieldTypeLoad)) return "getCharSequenceArray";
        if ("double".equalsIgnoreCase(fieldTypeLoad)) return "getDouble";
        if ("Double".equalsIgnoreCase(fieldTypeLoad)) return "getDouble"; // Wrapper class
        if ("double[]".equalsIgnoreCase(fieldTypeLoad)) return "getDoubleArray";
        if ("float".equalsIgnoreCase(fieldTypeLoad)) return "getFloat";
        if ("Float".equalsIgnoreCase(fieldTypeLoad)) return "getFloat"; // Wrapper class
        if ("float[]".equalsIgnoreCase(fieldTypeLoad)) return "getFloatArray";
        if ("IBinder".equalsIgnoreCase(fieldTypeLoad)) return "getBinder";
        if ("int".equalsIgnoreCase(fieldTypeLoad)) return "getInt";
        if ("Integer".equalsIgnoreCase(fieldTypeLoad)) return "getInt"; // Wrapper class
        if ("int[]".equalsIgnoreCase(fieldTypeLoad)) return "getIntArray";
        if ("long".equalsIgnoreCase(fieldTypeLoad)) return "getLong";
        if ("Long".equalsIgnoreCase(fieldTypeLoad)) return "getLong"; // Wrapper class
        if ("long[]".equalsIgnoreCase(fieldTypeLoad)) return "getLongArray";
        if ("Object".equalsIgnoreCase(fieldTypeLoad)) return "get";
        if ("Parcelable[]".equalsIgnoreCase(fieldTypeLoad)) return "getParcelableArray";
        if ("Serializable".equalsIgnoreCase(fieldTypeLoad)) return "getSerializable";
        if ("short".equalsIgnoreCase(fieldTypeLoad)) return "getShort";
        if ("Short".equalsIgnoreCase(fieldTypeLoad)) return "getShort"; // Wrapper class
        if ("short[]".equalsIgnoreCase(fieldTypeLoad)) return "getShortArray";
        if ("Size".equalsIgnoreCase(fieldTypeLoad)) return "getSize";
        if ("SizeF".equalsIgnoreCase(fieldTypeLoad)) return "getSizeF";
        if ("String".equalsIgnoreCase(fieldTypeLoad)) return "getString";
        if ("String[]".equalsIgnoreCase(fieldTypeLoad)) return "getStringArray";
        return null;
    }

    private PsiMethod addOnCreate(PsiClass psiClass) {
        // TODO
//        ThrowExMessages.showMessageDialog(mProject, "Unable to add onCreate() method!", "Error!", null);
        return null;
    }

    private boolean init(AnActionEvent e) {
        mInfoBuilder = new StringBuilder();
        mProject = e.getProject();
        mEditor = e.getRequiredData(CommonDataKeys.EDITOR);
        mDocument = mEditor.getDocument();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (mEditor == null || psiFile == null) return false;

        mOffset = mEditor.getCaretModel().getOffset();
        mElementAtCarret = psiFile.findElementAt(mOffset);
        if (mElementAtCarret == null) return false;

        mPsiClass = PsiTreeUtil.getParentOfType(mElementAtCarret, PsiClass.class);
        if (mPsiClass == null) return false;


        mElementFactory = JavaPsiFacade.getElementFactory(mProject);


        return true;
    }
}
