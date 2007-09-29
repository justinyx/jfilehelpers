package org.coury.jfilehelpers.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.coury.jfilehelpers.annotations.FixedLengthRecord;
import org.coury.jfilehelpers.annotations.IgnoreCommentedLines;
import org.coury.jfilehelpers.annotations.IgnoreFirst;
import org.coury.jfilehelpers.annotations.IgnoreLast;
import org.coury.jfilehelpers.engines.LineInfo;
import org.coury.jfilehelpers.enums.RecordCondition;
import org.coury.jfilehelpers.fields.FieldBase;
import org.coury.jfilehelpers.fields.FieldFactory;
import org.coury.jfilehelpers.fields.FixedLengthField;
import org.coury.jfilehelpers.helpers.ConditionHelper;
import org.coury.jfilehelpers.helpers.StringHelper;

public final class RecordInfo<T> {
	private FieldBase[] fields;
	private Class<T> recordClass;
	private Constructor<T> recordConstructor;

	private int ignoreFirst = 0;
	private int ignoreLast = 0;
	private boolean ignoreEmptyLines = false;
	private boolean ignoreEmptySpaces = false;
	private String commentMarker = null;
	private boolean commentAnyPlace = true;
	private RecordCondition recordCondition = RecordCondition.None;
	private String recordConditionSelector = "";

	private boolean notifyRead;
	private boolean notifyWrite;
	private String conditionRegEx = null;
	
	private int sizeHint = 32;
	
	//private ConverterBase converterProvider = null;

	private int fieldCount;
	
	public RecordInfo(Class<T> recordClass) {
		// this.recordObject = recordObject;
		this.recordClass = recordClass;
		initFields();
	}
	
	public T strToRecord(LineInfo line) {
		if (mustIgnoreLine(line.getLineStr())) {
			return null;
		}

		Object[] values = new Object[fieldCount];

		// array that holds the fields values
		T record = null;
		try {
			for (int i = 0; i < fieldCount; i++) {
				values[i] = fields[i].extractValue(line);
			}
	
			record = createRecordObject();
			for (int i = 0; i < fieldCount; i++) {
				Field f = record.getClass().getField(fields[i].getFieldInfo().getName());
				f.set(record, values[i]);
				// fields[i].getFieldInfo().set(record, values[i]);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Problems while reading field values from " + record + " object", e);
		}
		
		return record;
		
		// TODO Improve
//		CreateAssingMethods();
//
//        try
//        {
//            // Asign all values via dinamic method that creates an object and assign values
//           return mCreateHandler(mValues);
//        }
//        catch (InvalidCastException)
//        {
//            // Occurrs when the a custom converter returns an invalid value for the field.
//            for (int i = 0; i < mFieldCount; i++)
//            {
//                if (mValues[i] != null && ! mFields[i].mFieldType.IsInstanceOfType(mValues[i]))
//                    throw new ConvertException(null, mFields[i].mFieldType, mFields[i].mFieldInfo.Name, line.mReader.LineNumber, -1, "The converter for the field: " + mFields[i].mFieldInfo.Name + " returns an object of Type: " + mValues[i].GetType().Name + " and the field is of type: " + mFields[i].mFieldType.Name);
//            }
//            return null;
//        }
	}
	
	private T createRecordObject() throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
		try {
			return recordConstructor.newInstance();
		}
		catch (IllegalArgumentException e) {
			Object parameter = recordClass.getEnclosingClass().newInstance();
			return recordConstructor.newInstance(parameter);
		}
	}
		
	private boolean mustIgnoreLine(String line) {
		if (ignoreEmptyLines) {
			if ((ignoreEmptySpaces && line.trim().length() == 0) || line.length() == 0) {
				return true;
			}
		}
		
		if (commentMarker != null && commentMarker.length() > 0) {
			if ((commentAnyPlace && line.trim().startsWith(commentMarker)) || line.startsWith(commentMarker)) {
				return true;
			}
		}
		
		switch (recordCondition) {
		case ExcludeIfBegins:
			return ConditionHelper.beginsWith(line, recordConditionSelector);
			
		case IncludeIfBegins:
			return !ConditionHelper.beginsWith(line, recordConditionSelector);
			
		case ExcludeIfContains:
			return ConditionHelper.contains(line, recordConditionSelector);
			
		case IncludeIfContains:
			return !ConditionHelper.contains(line, recordConditionSelector);
			
		case ExcludeIfEnclosed:
			return ConditionHelper.enclosed(line, recordConditionSelector);
			
		case IncludeIfEnclosed:
			return !ConditionHelper.enclosed(line, recordConditionSelector);
			
		case ExcludeIfEnds:
			return ConditionHelper.endsWith(line, recordConditionSelector);
			
		case IncludeIfEnds:
			return !ConditionHelper.endsWith(line, recordConditionSelector);
			
		case ExcludeIfMatchRegex:
			return Pattern.matches(conditionRegEx, line);
			
		case IncludeIfMatchRegex:
			return !Pattern.matches(conditionRegEx, line);
			
		}
		
		return false;
	}

	@SuppressWarnings("unchecked")
	private void initFields() {
		IgnoreFirst igf = recordClass.getAnnotation(IgnoreFirst.class);
		if (igf != null) {
			this.ignoreFirst = igf.lines();
		}
		
		IgnoreLast igl = recordClass.getAnnotation(IgnoreLast.class);
		if (igl != null) {
			this.ignoreFirst = igl.lines();
		}
		
		this.ignoreEmptyLines = recordClass.isAnnotationPresent(IgnoreLast.class);
		
		IgnoreCommentedLines igc = recordClass.getAnnotation(IgnoreCommentedLines.class);
		if (igc != null) {
			this.commentMarker = igc.commentMarker();
			this.commentAnyPlace = igc.anyPlace();
		}
		
		// TODO ConditionalRecord

		/*
		// TODO Notifications
		if (typeof(INotifyRead).IsAssignableFrom(mRecordType))
			mNotifyRead = true;

		if (typeof(INotifyWrite).IsAssignableFrom(mRecordType))
			mNotifyWrite = true;
		*/

		try {
			recordConstructor = (Constructor<T>) recordClass.getConstructor();
		} catch (SecurityException e) {
			throw new RuntimeException(
					"The class " + recordClass.getName() + 
					" needs to be accessible to be used");
		} catch (NoSuchMethodException e) {
			boolean throwIt = true;

			try {
				if (recordClass.getEnclosingClass() != null) {
					recordConstructor = (Constructor<T>) recordClass.getConstructor(recordClass.getEnclosingClass());
				}
				throwIt = false;
			}
			catch (NoSuchMethodException e1) {
			}

			if (throwIt) {
				throw new RuntimeException(
						"The class " + recordClass.getName() + 
						" needs to have an empty constructor to be used");
			}
		}
		
		fields = createCoreFields(recordClass.getFields(), recordClass);
		fieldCount = this.fields.length;
		
		if (isFixedLength()) {
			sizeHint = 0;
			for (int i = 0; i < fieldCount; i++) {
				sizeHint += ((FixedLengthField) fields[i]).getFieldLength();
			}
		}
		
		if (fieldCount == 0) {
			throw new IllegalArgumentException("The record class " + recordClass.getName() + " don't contains any field.");
		}
	}

	private boolean isFixedLength() {
		return recordClass.isAnnotationPresent(FixedLengthRecord.class);
	}
	
	@SuppressWarnings("unchecked")
	private static FieldBase[] createCoreFields(Field[] fields, Class recordClass) {
		FieldBase field;
		List<FieldBase> fieldArr = new ArrayList<FieldBase>();
		
		boolean someOptional = false;
		for (Field f : fields) {
			field = FieldFactory.createField(f, recordClass, someOptional);
			if (field != null) {
				someOptional = field.isOptional();
				fieldArr.add(field);
				if (fieldArr.size() > 1) {
					fieldArr.get(fieldArr.size() - 2).setNextOptional(fieldArr.get(fieldArr.size() - 1).isOptional());
				}
			}
		}
		
		if (fieldArr.size() > 0) {
			fieldArr.get(0).setFirst(true);
			fieldArr.get(fieldArr.size() - 1).setLast(true);
		}
		
		return fieldArr.toArray(new FieldBase[] {});
	}

	public String toString() {
		return StringHelper.toStringBuilder(this);
	}

	public FieldBase[] getFields() {
		return fields;
	}

	public void setFields(FieldBase[] fields) {
		this.fields = fields;
	}

	public int getIgnoreFirst() {
		return ignoreFirst;
	}

	public void setIgnoreFirst(int ignoreFirst) {
		this.ignoreFirst = ignoreFirst;
	}

	public int getIgnoreLast() {
		return ignoreLast;
	}

	public void setIgnoreLast(int ignoreLast) {
		this.ignoreLast = ignoreLast;
	}

	public boolean isIgnoreEmptyLines() {
		return ignoreEmptyLines;
	}

	public void setIgnoreEmptyLines(boolean ignoreEmptyLines) {
		this.ignoreEmptyLines = ignoreEmptyLines;
	}

	public boolean isIgnoreEmptySpaces() {
		return ignoreEmptySpaces;
	}

	public void setIgnoreEmptySpaces(boolean ignoreEmptySpaces) {
		this.ignoreEmptySpaces = ignoreEmptySpaces;
	}

	public String getCommentMarker() {
		return commentMarker;
	}

	public void setCommentMarker(String commentMaker) {
		this.commentMarker = commentMaker;
	}

	public boolean isCommentAnyPlace() {
		return commentAnyPlace;
	}

	public void setCommentAnyPlace(boolean commentAnyPlace) {
		this.commentAnyPlace = commentAnyPlace;
	}

	public RecordCondition getRecordCondition() {
		return recordCondition;
	}

	public void setRecordCondition(RecordCondition recordCondition) {
		this.recordCondition = recordCondition;
	}

	public String getRecordConditionSelector() {
		return recordConditionSelector;
	}

	public void setRecordConditionSelector(String recordConditionSelector) {
		this.recordConditionSelector = recordConditionSelector;
	}

	public boolean isNotifyRead() {
		return notifyRead;
	}

	public void setNotifyRead(boolean notifyRead) {
		this.notifyRead = notifyRead;
	}

	public boolean isNotifyWrite() {
		return notifyWrite;
	}

	public void setNotifyWrite(boolean notifyWrite) {
		this.notifyWrite = notifyWrite;
	}

	public String getConditionRegEx() {
		return conditionRegEx;
	}

	public void setConditionRegEx(String conditionRegEx) {
		this.conditionRegEx = conditionRegEx;
	}

	public int getFieldCount() {
		return fieldCount;
	}

	public void setFieldCount(int fieldCount) {
		this.fieldCount = fieldCount;
	}

	public Constructor<T> getRecordConstructor() {
		return recordConstructor;
	}

	public void setRecordConstructor(Constructor<T> recordConstructor) {
		this.recordConstructor = recordConstructor;
	}
}