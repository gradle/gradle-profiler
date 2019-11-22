package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

/**
 * This class applies a mutation to an Android layout file by adding an extra, hidden view at the bottom of the layout.
 * It supports both DataBinding layouts (wrapped in <layout></layout> tags) and traditional Android layouts.
 *
 * Note: This mutator does not support layouts with a single view in them - it requires a valid ViewGroup to contain the
 * new view. Attempting to mutate a layout that does not have a ViewGroup as its root element will result in an
 * invalid layout file.
 */
public class ApplyChangeToAndroidLayoutFileMutator extends AbstractFileChangeMutator {
    public ApplyChangeToAndroidLayoutFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos = text.lastIndexOf("</layout>");
        if (insertPos < 0) {
            applyChangeToNonDataBindingLayout(context, text);
        } else {
            applyChangeToDataBindingLayout(context, text, insertPos);
        }
    }

    private void applyChangeToDataBindingLayout(BuildContext context, StringBuilder text, int tagEndPosition) {
        String substring = text.substring(0, tagEndPosition);
        int insertPos = substring.lastIndexOf("</");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android layout file " + sourceFile + " to apply changes position " + insertPos);
        }

        text.insert(insertPos, generateUniqueViewItem(context));
    }

    private void applyChangeToNonDataBindingLayout(BuildContext context, StringBuilder text) {
        int insertPos = text.lastIndexOf("</");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android layout file " + sourceFile + " to apply changes");
        }

        text.insert(insertPos, generateUniqueViewItem(context));
    }

    private String generateUniqueViewItem(BuildContext context) {
        return "<View \n" +
               "    android:id=\"@+id/view" + context.getUniqueBuildId() + "\"\n" +
               "    android:visibility=\"gone\"\n" +
               "    android:layout_width=\"5dp\"\n" +
               "    android:layout_height=\"5dp\"/>\n" +
               "\n";
    }
}
