package edu.oregonstate.cope.eclipse.listeners;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.internal.UIPlugin;
import org.eclipse.ui.part.FileEditorInput;

import edu.oregonstate.cope.eclipse.COPEPlugin;

public class CommandExecutionListener implements IExecutionListener {

	private static boolean saveInProgress = false;
	private static boolean cutInProgress = false;
	private static boolean pasteInProgress = false;
	private static boolean undoInProgress = false;
	private static boolean redoInProgress = false;

	@Override
	public void preExecute(String commandId, ExecutionEvent event) {
		if (isCopy(commandId)) {
			recordCopy();
		}
		if (isCut(commandId)) {
			cutInProgress = true;
		}
		if (isPaste(commandId))
			pasteInProgress = true;
		if (isUndo(commandId))
			undoInProgress = true;
		if (isRedo(commandId))
			redoInProgress = true;
		if (isFileSave(commandId))
			saveInProgress  = true;
	}

	private boolean isCopy(String commandId) {
		return commandId.equalsIgnoreCase(IWorkbenchCommandConstants.EDIT_COPY);
	}
	
	private boolean isCut(String commandId) {
		return commandId.equals(IWorkbenchCommandConstants.EDIT_CUT);
	}
	
	private boolean isPaste(String commandId) {
		return commandId.equals(IWorkbenchCommandConstants.EDIT_PASTE);
	}
	
	private boolean isUndo(String commandId) {
		return commandId.equals(IWorkbenchCommandConstants.EDIT_UNDO);
	}
	
	private boolean isRedo(String commandId) {
		return commandId.equals(IWorkbenchCommandConstants.EDIT_REDO);
	}

	private void recordCopy() {
		ISelection selection = UIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getSelectionService().getSelection();
		
		if (isInIgnoredProjectsList())
			return;
		
		if (selection instanceof ITextSelection) {
			ITextSelection textSelection = (ITextSelection) selection;
			int offset = textSelection.getOffset();
			int length = textSelection.getLength();
			String text = textSelection.getText();
			String sourceFile = getSourceFile();
			COPEPlugin.getDefault().getClientRecorder().recordCopy(sourceFile, offset, length, text);
		}
	}

	private boolean isInIgnoredProjectsList() {
		FileEditorInput fileEditorInput = getFileEditorInput();

		if (fileEditorInput != null && fileEditorInput.getFile().getProject() != null) {
			List<String> ignoreProjectsList = COPEPlugin.getDefault().getIgnoreProjectsList();
			return ignoreProjectsList.contains(fileEditorInput.getFile().getProject().getName());
		}

		return false;
	}

	@SuppressWarnings("restriction")
	private String getSourceFile() {
		FileEditorInput fileEditorInput = getFileEditorInput();

		if (fileEditorInput != null)
			return fileEditorInput.getFile().getFullPath().toPortableString();

		return "";
	}

	private FileEditorInput getFileEditorInput() {
		IEditorPart activeEditor = UIPlugin.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		IEditorInput editorInput = activeEditor.getEditorInput();
		
		FileEditorInput fileEditorInput = null;

		if (editorInput instanceof FileEditorInput)
			fileEditorInput = (FileEditorInput) editorInput;
		
		return fileEditorInput;
	}

	private boolean isFileSave(String commandId) {
		return commandId.equals(IWorkbenchCommandConstants.FILE_SAVE) || commandId.equalsIgnoreCase(IWorkbenchCommandConstants.FILE_SAVE_ALL);
	}

	@Override
	public void postExecuteSuccess(String commandId, Object returnValue) {
		if (isFileSave(commandId))
			saveInProgress = false;
		if (isCut(commandId))
			cutInProgress = false;
		if (isPaste(commandId))
			pasteInProgress = false;
		if (isUndo(commandId))
			undoInProgress = false;
		if (isRedo(commandId))
			redoInProgress = false;
	}

	@Override
	public void postExecuteFailure(String commandId, ExecutionException exception) {
		if (isFileSave(commandId))
			saveInProgress = false;
		if (isCut(commandId))
			cutInProgress = false;
		if (isPaste(commandId))
			pasteInProgress = false;
		if (isUndo(commandId))
			undoInProgress = false;
		if (isRedo(commandId))
			redoInProgress = false;
	}

	@Override
	public void notHandled(String commandId, NotHandledException exception) {
	}

	public static boolean isSaveInProgress() {
		return saveInProgress;
	}
	
	public static boolean isCutInProgress() {
		return cutInProgress;
	}
	
	public static boolean isPasteInProgress() {
		return pasteInProgress;
	}
	
	public static boolean isUndoInProgress() {
		return undoInProgress;
	}
	
	public static boolean isRedoInProgress() {
		return redoInProgress;
	}
}