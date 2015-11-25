package edu.uw.easysrl.util;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;

public class CountDictionary {
	TObjectIntHashMap<String> str2index;
	ArrayList<String> index2str;
	ArrayList<Integer> index2count;
	boolean canGrow;

	// Special tokens
	public String unknownToken = "<UNK>";
	public int unknownTokenId;
	
	public CountDictionary() {
		str2index = new TObjectIntHashMap<String>();
		index2str = new ArrayList<String>();
		index2count = new ArrayList<Integer>();
		canGrow = true;
		unknownTokenId = addString(unknownToken, 99999);
	}
	
	// Copy from existing dictionary.
	public CountDictionary(CountDictionary dict, boolean canGrow) {
		this();
		for (int sid = 0; sid < dict.size(); sid ++) {
			String str = dict.getString(sid);
			index2str.add(str);
			index2count.add(dict.getCount(sid));
			str2index.put(str, sid);
		}
		this.canGrow = canGrow;
	}
	
	public CountDictionary(CountDictionary dict, int minFrequency, boolean canGrow) {
		this();
		for (int sid = 0; sid < dict.size(); sid ++) {
			int freq = dict.getCount(sid);
			if (freq < minFrequency) {
				continue;
			}
			String str = dict.getString(sid);
			str2index.put(str, index2str.size());
			index2str.add(str);
			index2count.add(freq);
		}
		this.canGrow = canGrow;
	}
	
	public void clearCounts() {
		for (int sid = 0; sid < index2count.size(); sid ++) {
			index2count.set(sid, 0);
		}
	}
	
	public void insertTuple(int id, String str, int freq) {
		assert id == index2str.size() && canGrow;
		index2str.add(str);
		index2count.add(freq);
		str2index.put(str,id);
	}
 	
	public int addString(String str) {
		if (str2index.contains(str)) {
			int sid = str2index.get(str);
			int count = index2count.get(sid);
			index2count.set(sid, count + 1);
			return sid;
		} else if (canGrow) {
			int sid = index2str.size();
			index2str.add(str);
			index2count.add(1);
			str2index.put(str, sid);
			return sid;
		}
		return unknownTokenId;
	}

	public int addString(String str, int count) {
		if (str2index.contains(str)) {
			int sid = str2index.get(str);
			index2count.set(sid, index2count.get(sid) + count);
			return sid;
		} else if (canGrow) {
			int sid = index2str.size();
			index2str.add(str);
			index2count.add(count);
			str2index.put(str, sid);
			return sid;
		}
		return unknownTokenId;
	}

	public void freeze() {
		this.canGrow = false;
	}

	public void unfreeze() {
		this.canGrow = true;
	}

	public boolean canGrow() {
		return canGrow;
	}

	public boolean contains(String str) {
		return str2index.contains(str);
	}
	
	public int lookupString(String str) {
		if (!str2index.contains(str)) {
			return -1;
		}
		return str2index.get(str);
	}
	
	public int getCount(String str) {
		if (!str2index.contains(str)) {
			return 0;
		}
		return index2count.get(str2index.get(str));
	}
	
	public int getCount(int index) {
		return (index < index2count.size()) ? index2count.get(index) : 0; 
	}
	
	public int size() {
		return index2str.size();
	}
	
	// TODO: handle -1 index value.
	public String getString(int index) {
		return index2str.get(index);
	}
	
	public String[] getStringArray(int[] indices) {
		String[] strings = new String[indices.length];
		for (int i = 0; i < indices.length; i++) {
			strings[i] = getString(indices[i]);
		}
		return strings;
	}

	public ArrayList<String> getStrings() {
		return index2str;
	}
	
	public int getTotalCount() {
		int totalCount = 0;
		for (int c : index2count) {
			totalCount += c;
		}
		return totalCount;
	}
	
	public void prettyPrint() {
		for (int i = 0; i < size(); i++) {
			System.out.println(String.format("%d\t%s\t%d", i, index2str.get(i), index2count.get(i)));
		}
	}

}