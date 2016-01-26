package br.com.thinkti.android.filechooser;

public class Option implements Comparable<Option>{
	private String name;
	private String data;
	private String path;
	private boolean folder;
	private boolean parent;
	private boolean back;
	
	public Option(String n,String d,String p, boolean folder, boolean parent, boolean back)
	{
		name = n;
		data = d;
		path = p;
		this.folder = folder;
		this.parent = parent;
		this.back = back;
	}
	public String getName()
	{
		return name;
	}
	public String getData()
	{
		return data;
	}
	public String getPath()
	{
		return path;
	}
	@Override
	public int compareTo(Option o) {
		if(this.name != null)
			return this.name.toLowerCase().compareTo(o.getName().toLowerCase()); 
		else 
			throw new IllegalArgumentException();
	}
	public boolean isFolder() {
		return folder;
	}
	public boolean isParent() {
		return parent;
	}
	public boolean isBack() {
		return back;
	}
	public void setBack(boolean back) {
		this.back = back;
	}
}
