package com.fasterxml.jackson.core.util;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;

/**
 * Default {@link PrettyPrinter} implementation that uses 2-space
 * indentation with platform-default linefeeds.
 * Usually this class is not instantiated directly, but instead
 * instantiated by {@code JsonFactory} or databind level mapper.
 *<p>
 * If you override this class, take note of {@link Instantiatable},
 * as subclasses will still create an instance of DefaultPrettyPrinter.
 */
@SuppressWarnings("serial")
public class DefaultPrettyPrinter
    implements PrettyPrinter, Instantiatable<DefaultPrettyPrinter>,
        java.io.Serializable
{
    private static final long serialVersionUID = 1;

    /**
     * Constant that specifies default "root-level" separator to use between
     * root values: a single space character.
     */
    public final static SerializedString DEFAULT_ROOT_VALUE_SEPARATOR = new SerializedString(" ");

    /**
     * Interface that defines objects that can produce indentation used
     * to separate object entries and array values. Indentation in this
     * context just means insertion of white space, independent of whether
     * linefeeds are output.
     */
    public interface Indenter
    {
        void writeIndentation(JsonGenerator g, int level) throws JacksonException;

        /**
         * @return True if indenter is considered inline (does not add linefeeds),
         *   false otherwise
         */
        boolean isInline();
    }

    // // // Config, indentation

    /**
     * By default, let's use only spaces to separate array values.
     */
    protected Indenter _arrayIndenter = FixedSpaceIndenter.instance();

    /**
     * By default, let's use linefeed-adding indenter for separate
     * object entries. We'll further configure indenter to use
     * system-specific linefeeds, and 2 spaces per level (as opposed to,
     * say, single tabs)
     */
    protected Indenter _objectIndenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;

    /**
     * String printed between root-level values, if any.
     */
    protected final SerializableString _rootSeparator;

    // // // Config, other white space configuration

    /**
     * By default we will add spaces around colons used to
     * separate Object property names and values.
     * If disabled, will not use spaces around colon.
     */
    protected boolean _spacesInObjectEntries = true;

    // // // State:

    /**
     * Number of open levels of nesting. Used to determine amount of
     * indentation to use.
     */
    protected transient int _nesting;

    protected Separators _separators;

    protected String _objectFieldValueSeparatorWithSpaces;

    /*
    /**********************************************************************
    /* Life-cycle (construct, configure)
    /**********************************************************************
     */

    public DefaultPrettyPrinter() {
        this(DEFAULT_ROOT_VALUE_SEPARATOR);
    }

    /**
     * Constructor that specifies separator String to use between root values;
     * if null, no separator is printed.
     *<p>
     * Note: simply constructs a {@link SerializedString} out of parameter,
     * calls {@link #DefaultPrettyPrinter(SerializableString)}
     *
     * @param rootSeparator String to use as root value separator
     */
    public DefaultPrettyPrinter(String rootSeparator) {
        this((rootSeparator == null) ? null : new SerializedString(rootSeparator));
    }

    /**
     * Constructor that specifies separator String to use between root values;
     * if null, no separator is printed.
     *
     * @param rootSeparator String to use as root value separator
     */
    public DefaultPrettyPrinter(SerializableString rootSeparator) {
        _rootSeparator = rootSeparator;
        withSeparators(DEFAULT_SEPARATORS);
    }
    
    public DefaultPrettyPrinter(DefaultPrettyPrinter base) {
        this(base, base._rootSeparator);
    }

    public DefaultPrettyPrinter(DefaultPrettyPrinter base,
            SerializableString rootSeparator)
    {
        _arrayIndenter = base._arrayIndenter;
        _objectIndenter = base._objectIndenter;
        _spacesInObjectEntries = base._spacesInObjectEntries;
        _nesting = base._nesting;

        _separators = base._separators;
        _objectFieldValueSeparatorWithSpaces = base._objectFieldValueSeparatorWithSpaces;

        _rootSeparator = rootSeparator;
    }

    public DefaultPrettyPrinter withRootSeparator(SerializableString rootSeparator)
    {
        if (_rootSeparator == rootSeparator ||
                (rootSeparator != null && rootSeparator.equals(_rootSeparator))) {
            return this;
        }
        return new DefaultPrettyPrinter(this, rootSeparator);
    }

    /**
     * @param rootSeparator Root-level value separator to use
     *
     * @return This pretty-printer instance (for call chaining)
     */
    public DefaultPrettyPrinter withRootSeparator(String rootSeparator) {
        return withRootSeparator((rootSeparator == null) ? null : new SerializedString(rootSeparator));
    }

    public void indentArraysWith(Indenter i) {
        _arrayIndenter = (i == null) ? NopIndenter.instance() : i;
    }

    public void indentObjectsWith(Indenter i) {
        _objectIndenter = (i == null) ? NopIndenter.instance() : i;
    }

    public DefaultPrettyPrinter withArrayIndenter(Indenter i) {
        if (i == null) {
            i = NopIndenter.instance();
        }
        if (_arrayIndenter == i) {
            return this;
        }
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter(this);
        pp._arrayIndenter = i;
        return pp;
    }

    public DefaultPrettyPrinter withObjectIndenter(Indenter i) {
        if (i == null) {
            i = NopIndenter.instance();
        }
        if (_objectIndenter == i) {
            return this;
        }
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter(this);
        pp._objectIndenter = i;
        return pp;
    }

    /**
     * "Mutant factory" method that will return a pretty printer instance
     * that does use spaces inside object entries; if 'this' instance already
     * does this, it is returned; if not, a new instance will be constructed
     * and returned.
     *
     * @return This pretty-printer instance (for call chaining)
     */
    public DefaultPrettyPrinter withSpacesInObjectEntries() {
        return _withSpaces(true);
    }

    /**
     * "Mutant factory" method that will return a pretty printer instance
     * that does not use spaces inside object entries; if 'this' instance already
     * does this, it is returned; if not, a new instance will be constructed
     * and returned.
     *
     * @return This pretty-printer instance (for call chaining)
     */
    public DefaultPrettyPrinter withoutSpacesInObjectEntries() {
        return _withSpaces(false);
    }

    protected DefaultPrettyPrinter _withSpaces(boolean state)
    {
        if (_spacesInObjectEntries == state) {
            return this;
        }
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter(this);
        pp._spacesInObjectEntries = state;
        return pp;
    }

    /**
     * Method for configuring separators for this pretty-printer to use
     *
     * @param separators Separator definitions to use
     *
     * @return This pretty-printer instance (for call chaining)
     */
    public DefaultPrettyPrinter withSeparators(Separators separators) {
        _separators = separators;
        _objectFieldValueSeparatorWithSpaces = " " + separators.getObjectNameValueSeparator() + " ";
        return this;
    }

    /*
    /**********************************************************************
    /* Instantiatable impl
    /**********************************************************************
     */

    @Override
    public DefaultPrettyPrinter createInstance() {
        if (getClass() != DefaultPrettyPrinter.class) {
            throw new IllegalStateException("Failed `createInstance()`: "+getClass().getName()
                    +" does not override method; it has to");
        }
        return new DefaultPrettyPrinter(this);
    }

    /*
    /**********************************************************************
    /* PrettyPrinter impl
    /**********************************************************************
     */

    @Override
    public void writeRootValueSeparator(JsonGenerator g) throws JacksonException
    {
        if (_rootSeparator != null) {
            g.writeRaw(_rootSeparator);
        }
    }

    @Override
    public void writeStartObject(JsonGenerator g) throws JacksonException
    {
        g.writeRaw('{');
        if (!_objectIndenter.isInline()) {
            ++_nesting;
        }
    }

    @Override
    public void beforeObjectEntries(JsonGenerator g) throws JacksonException
    {
        _objectIndenter.writeIndentation(g, _nesting);
    }

    /**
     * Method called after the object property name has been output, but
     * before the value is output.
     *<p>
     * Default handling (without pretty-printing) will output a single
     * colon to separate the two. Pretty-printer is
     * to output a colon as well, but can surround that with other
     * (white-space) decoration.
     */
    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator g) throws JacksonException
    {
        if (_spacesInObjectEntries) {
            g.writeRaw(_objectFieldValueSeparatorWithSpaces);
        } else {
            g.writeRaw(_separators.getObjectNameValueSeparator());
        }
    }

    /**
     * Method called after an object entry (field:value) has been completely
     * output, and before another value is to be output.
     *<p>
     * Default handling (without pretty-printing) will output a single
     * comma to separate the two. Pretty-printer is
     * to output a comma as well, but can surround that with other
     * (white-space) decoration.
     */
    @Override
    public void writeObjectEntrySeparator(JsonGenerator g) throws JacksonException
    {
        g.writeRaw(_separators.getObjectEntrySeparator());
        _objectIndenter.writeIndentation(g, _nesting);
    }

    @Override
    public void writeEndObject(JsonGenerator g, int nrOfEntries) throws JacksonException
    {
        if (!_objectIndenter.isInline()) {
            --_nesting;
        }
        if (nrOfEntries > 0) {
            _objectIndenter.writeIndentation(g, _nesting);
        } else {
            g.writeRaw(' ');
        }
        g.writeRaw('}');
    }

    @Override
    public void writeStartArray(JsonGenerator g) throws JacksonException
    {
        if (!_arrayIndenter.isInline()) {
            ++_nesting;
        }
        g.writeRaw('[');
    }

    @Override
    public void beforeArrayValues(JsonGenerator g) throws JacksonException {
        _arrayIndenter.writeIndentation(g, _nesting);
    }

    /**
     * Method called after an array value has been completely
     * output, and before another value is to be output.
     *<p>
     * Default handling (without pretty-printing) will output a single
     * comma to separate the two. Pretty-printer is
     * to output a comma as well, but can surround that with other
     * (white-space) decoration.
     */
    @Override
    public void writeArrayValueSeparator(JsonGenerator g) throws JacksonException
    {
        g.writeRaw(_separators.getArrayValueSeparator());
        _arrayIndenter.writeIndentation(g, _nesting);
    }

    @Override
    public void writeEndArray(JsonGenerator g, int nrOfValues) throws JacksonException
    {
        if (!_arrayIndenter.isInline()) {
            --_nesting;
        }
        if (nrOfValues > 0) {
            _arrayIndenter.writeIndentation(g, _nesting);
        } else {
            g.writeRaw(' ');
        }
        g.writeRaw(']');
    }

    /*
    /**********************************************************************
    /* Helper classes
    /**********************************************************************
     */

    /**
     * Dummy implementation that adds no indentation whatsoever
     */
    public static class NopIndenter
        implements Indenter, java.io.Serializable
    {
        private static final NopIndenter _nopInstance = new NopIndenter();

        public static NopIndenter instance() { return _nopInstance; }

        @Override
        public void writeIndentation(JsonGenerator g, int level) { }

        @Override
        public boolean isInline() { return true; }
    }

    /**
     * This is a very simple indenter that only adds a
     * single space for indentation. It is used as the default
     * indenter for array values.
     */
    public static class FixedSpaceIndenter extends NopIndenter
    {
        private static final FixedSpaceIndenter _fixedInstance = new FixedSpaceIndenter();

        public static FixedSpaceIndenter instance() { return _fixedInstance; }

        @Override
        public void writeIndentation(JsonGenerator g, int level) throws JacksonException
        {
            g.writeRaw(' ');
        }

        @Override
        public boolean isInline() { return true; }
    }
}
