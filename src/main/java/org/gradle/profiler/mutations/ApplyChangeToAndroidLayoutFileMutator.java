package org.gradle.profiler.mutations;

import java.io.File;

/**
 * This class applies a mutation to an Android layout file by adding an extra, hidden view at the bottom of the layout.
 * It supports both DataBinding layouts and traditional Android layouts.
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
    protected void applyChangeTo(StringBuilder text) {
        // We need to handle both databinding layouts, which end with </layout>, and non, which can end with any layout item closing tag
        // Handle DataBinding first
        int insertPos = text.lastIndexOf("</layout>");
        if (insertPos < 0) {
            // handle not databinding
            applyChangeToNonDataBindingLayout(text);
        } else {
            applyChangeToDataBindingLayout(text, insertPos);
        }
    }

    private void applyChangeToDataBindingLayout(StringBuilder text, int tagEndPosition) {
        // Find the closing tag before the </layout>, which will be the closing of the top-level layout item we want to add to
        String substring = text.substring(0, tagEndPosition);
        int insertPos = substring.lastIndexOf("</");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android layout file " + sourceFile + " to apply changes position " + insertPos);
        }

        text.insert(insertPos, generateUniqueViewItem());
    }

    private void applyChangeToNonDataBindingLayout(StringBuilder text) {
        // For non-databinding layouts, we can just append right before the last item ends, which will be some form of a layout item
        int insertPos = text.lastIndexOf("</");
        if (insertPos < 0) {
            throw new IllegalArgumentException("Cannot parse android layout file " + sourceFile + " to apply changes");
        }

        text.insert(insertPos, generateUniqueViewItem());
    }

    private String generateUniqueViewItem() {
        return "<View \n" +
               "    android:id=\"@+id/view" + getUniqueText() + "\"\n" +
               "    android:visibility=\"gone\"\n" +
               "    android:layout_width=\"5dp\"\n" +
               "    android:layout_height=\"5dp\"/>\n" +
               "\n";
    }
}
