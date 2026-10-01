/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2016 Chihiro Hio, Aaron Madlon-Kay
               Home page: https://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.externalfinder.item;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.omegat.externalfinder.item.PlaceholderTemplate.Context;
import org.omegat.util.OStrings;

/**
 * A data class representing an ExternalFinder "url". Immutable. Optionally use
 * {@link Builder} to construct.
 */
public class ExternalFinderItemURL {

    private final String url;
    private final ExternalFinderItem.TARGET target;
    private final ExternalFinderItem.ENCODING encoding;

    public ExternalFinderItemURL(String url, ExternalFinderItem.TARGET target,
            ExternalFinderItem.ENCODING encoding) {
        this.url = url;
        this.target = target;
        this.encoding = encoding;
    }

    public String getURL() {
        return url;
    }

    public ExternalFinderItem.TARGET getTarget() {
        return target;
    }

    public ExternalFinderItem.ENCODING getEncoding() {
        return encoding;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((encoding == null) ? 0 : encoding.hashCode());
        result = prime * result + ((target == null) ? 0 : target.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ExternalFinderItemURL other = (ExternalFinderItemURL) obj;
        if (encoding != other.encoding) {
            return false;
        }
        if (target != other.target) {
            return false;
        }
        if (url == null) {
            if (other.url != null) {
                return false;
            }
        } else if (!url.equals(other.url)) {
            return false;
        }
        return true;
    }

    /** Whether the URL reads the editor selection; see {@link PlaceholderTemplate}. */
    public boolean usesSelection() {
        return PlaceholderTemplate.usesSelection(url);
    }

    public URI generateURL(String findingWords) throws UnsupportedEncodingException, URISyntaxException {
        return generateURL(Context.ofSelection(findingWords));
    }

    public URI generateURL(Context context) throws URISyntaxException {
        return generateURL(url, encoding, context);
    }

    private static URI generateURL(String url, ExternalFinderItem.ENCODING encoding, Context context)
            throws URISyntaxException {
        return new URI(PlaceholderTemplate.parse(url).resolve(context, encoding::apply));
    }

    public static final class Builder {
        private String url;
        private ExternalFinderItem.TARGET target = ExternalFinderItem.TARGET.BOTH;
        private ExternalFinderItem.ENCODING encoding = ExternalFinderItem.ENCODING.DEFAULT;

        public static Builder from(ExternalFinderItemURL item) {
            return new Builder().setURL(item.getURL()).setTarget(item.getTarget())
                    .setEncoding(item.getEncoding());
        }

        public Builder setURL(String url) {
            this.url = url;
            return this;
        }

        public String getURL() {
            return url;
        }

        public Builder setTarget(ExternalFinderItem.TARGET target) {
            this.target = target;
            return this;
        }

        public ExternalFinderItem.TARGET getTarget() {
            return target;
        }

        public Builder setEncoding(ExternalFinderItem.ENCODING encoding) {
            this.encoding = encoding;
            return this;
        }

        public ExternalFinderItem.ENCODING getEncoding() {
            return encoding;
        }

        public ExternalFinderItemURL build() throws ExternalFinderValidationException {
            validate();
            return new ExternalFinderItemURL(url, target, encoding);
        }

        /**
         * Check the current builder parameters to see if they constitute a valid URL.
         *
         * @return A sample URL illustrating what the output will look like
         * @throws ExternalFinderValidationException
         *             If any parameter is not valid
         */
        public URI validate() throws ExternalFinderValidationException {
            if (url == null) {
                throw new ExternalFinderValidationException(
                        OStrings.getString("EXTERNALFINDER_URL_ERROR_NOURL"));
            }
            if (!PlaceholderTemplate.parse(url).hasPlaceholders()) {
                throw new ExternalFinderValidationException(
                        OStrings.getString("EXTERNALFINDER_PLACEHOLDER_ERROR_NONE"));
            }
            if (target == null) {
                throw new ExternalFinderValidationException("EXTERNALFINDER_URL_ERROR_NOTARGET");
            }
            if (encoding == null) {
                throw new ExternalFinderValidationException("EXTERNALFINDER_URL_ERROR_NOENCODING");
            }
            try {
                return generateSampleURL();
            } catch (Throwable e) {
                throw new ExternalFinderValidationException(e);
            }
        }

        public URI generateSampleURL() throws URISyntaxException {
            return generateURL(url, encoding,
                    Context.sample(target == ExternalFinderItem.TARGET.NON_ASCII_ONLY));
        }
    }
}
