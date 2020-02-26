/**
 * Copyright (c) 2012, Ben Fortuna
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  o Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *  o Neither the name of Ben Fortuna nor the names of any other contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.fortuna.ical4j.model.property;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.parameter.TzId;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.util.Strings;
import net.fortuna.ical4j.validate.ParameterValidator;
import net.fortuna.ical4j.validate.ValidationException;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Optional;

/**
 * $Id$
 * <p/>
 * Created on 9/07/2005
 * <p/>
 * Base class for properties with a DATE or DATE-TIME value. Note that some sub-classes may only allow either a DATE or
 * a DATE-TIME value, for which additional rules/validation should be specified.
 *
 * Note that generics have been introduced as part of the migration to the new Java Date/Time API.
 * Date properties should now indicate the applicable {@link Temporal} type for the property.
 *
 * For example:
 *
 * <ul>
 *     <li>UTC-based properties should use {@link java.time.Instant} to represent UTC time</li>
 *     <li>Date-only properties should use {@link java.time.LocalDate} to represent a date value</li>
 *     <li>Date-time properties should use {@link java.time.ZonedDateTime} to represent a date-time value influenced by timezone rules</li>
 * </ul>
 *
 * @author Ben Fortuna
 */
public abstract class DateProperty<T extends Temporal> extends Property {

    private static final long serialVersionUID = 3160883132732961321L;

    private TemporalAdapter<T> date;

    /**
     * @param name       the property name
     * @param parameters a list of initial parameters
     */
    public DateProperty(final String name, final ParameterList parameters, PropertyFactory factory) {
        super(name, parameters, factory);
    }

    /**
     * @param name the property name
     */
    public DateProperty(final String name, PropertyFactory factory) {
        super(name, factory);
    }

    /**
     * This method will attempt to dynamically cast the internal {@link Temporal} value to the
     * required return value.
     *
     * e.g. LocalDate localDate = dateProperty.getDate();
     *
     * @return Returns the date.
     */
    public T getDate() {
        if (date != null) {
            Optional<TzId> tzId = Optional.ofNullable(getParameter(Parameter.TZID));
            if (tzId.isPresent()) {
                return (T) date.toLocalTime(tzId.get().toZoneId());
            } else {
                return date.getTemporal();
            }
        } else {
            return null;
        }
    }

    /**
     * Sets the date value of this property. The timezone and value of this
     * instance will also be updated accordingly.
     *
     * @param date The date to set.
     */
    public void setDate(T date) {
        if (date != null) {
            this.date = new TemporalAdapter<>(date);
        } else {
            this.date = null;
        }
    }

    /**
     * Default setValue() implementation. Allows for either DATE or DATE-TIME values.
     *
     * Note that this method will use the system default zone rules to parse the string value. For parsing string
     * values in a different timezone use {@link TemporalAdapter#parse(String, ZoneId)} and
     * {@link DateProperty#setDate(Temporal)}.
     *
     * @param value a string representation of a DATE or DATE-TIME value
     * @throws ParseException where the specified value is not a valid DATE or DATE-TIME
     *                        representation
     */
    public void setValue(final String value) throws DateTimeParseException {
        // value can be either a date-time or a date..
        if (value != null && !value.isEmpty()) {
            TzId tzId = getParameter(Parameter.TZID);
            if (tzId != null) {
                this.date = (TemporalAdapter<T>) TemporalAdapter.parse(value, tzId);
            } else {
                this.date = TemporalAdapter.parse(value);
            }
        } else {
            this.date = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getValue() {
        return Strings.valueOf(date);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return getDate() != null ? getDate().hashCode() : 0;
    }

    /**
     * Indicates whether the current date value is specified in UTC time.
     *
     * @return true if the property is in UTC time, otherwise false
     */
    public final boolean isUtc() {
        return date != null && TemporalAdapter.isUtc(date.getTemporal());
    }

    /**
     * {@inheritDoc}
     */
    public void validate() throws ValidationException {

        /*
         * ; the following are optional, ; but MUST NOT occur more than once (";" "VALUE" "=" ("DATE-TIME" / "DATE")) /
         * (";" tzidparam) /
         */

        /*
         * ; the following is optional, ; and MAY occur more than once (";" xparam)
         */

        ParameterValidator.assertOneOrLess(Parameter.VALUE,
                getParameters());

        if (isUtc()) {
            ParameterValidator.assertNone(Parameter.TZID,
                    getParameters());
        } else {
            ParameterValidator.assertOneOrLess(Parameter.TZID,
                    getParameters());
        }

        final Value value = getParameter(Parameter.VALUE);

        if (date != null) {
            if (date.getTemporal() instanceof LocalDate) {
                if (value == null) {
                    throw new ValidationException("VALUE parameter [" + Value.DATE + "] must be specified for DATE instance");
                } else if (!Value.DATE.equals(value)) {
                    throw new ValidationException("VALUE parameter [" + value + "] is invalid for DATE instance");
                }
            } else {
                if (value != null && !Value.DATE_TIME.equals(value)) {
                    throw new ValidationException("VALUE parameter [" + value + "] is invalid for DATE-TIME instance");
                }

                if (date.getTemporal() instanceof ZonedDateTime) {
                    ZonedDateTime dateTime = (ZonedDateTime) date.getTemporal();

                    // ensure tzid matches date-time timezone..
                    final TzId tzId = getParameter(Parameter.TZID);
                    if (tzId == null || !tzId.toZoneId().equals(dateTime.getZone())) {
                        throw new ValidationException("TZID parameter [" + tzId + "] does not match the timezone ["
                                + dateTime.getZone().getId() + "]");
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Property copy() throws URISyntaxException, ParseException {
        final DateProperty<T> copy = (DateProperty<T>) super.copy();
        if (date != null) {
            copy.date = date;
        }
        return copy;
    }
}
