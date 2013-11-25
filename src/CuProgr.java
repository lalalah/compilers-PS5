import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;


public abstract class CuProgr {
	protected String text = "";
	protected String ctext = "";
	//the first node of CFG
	protected CuStat entry = null;
	protected ArrayList<CuStat> nodes = new ArrayList<CuStat>();
	@Override public String toString() {
		return text;
	}
	//added for project 5
	public void toHIR() {}
	public void buildCFG() {}
	//added for project 4
	protected List<String> newVars = new ArrayList<String>();
	//new functions
	public void add_prg(CuProgr p){}
	public void add_lastStat(CuStat s) {}
	
	public void calculateType(CuContext context) throws NoSuchTypeException {}
	
	public abstract String toC(ArrayList<String> localVars);
	
	public void buildSets() {}
	
	public void setUseDef() {}
	
	public void printHIR() {}
}

class FullPrg extends CuProgr {
	List<CuProgr> elements = new ArrayList<CuProgr>();
	CuStat s;
	List<CuStat> statements = new ArrayList<CuStat>();
	public void add_prg(CuProgr p){
		elements.add(p);
	}
	public void add_lastStat(CuStat s) {
		this.s = s;
	}
	@Override public void toHIR() {
		for (CuProgr pr : elements) 
			pr.toHIR();
			s = s.toHIR();
		if (Helper.debug) {
			System.out.println(s.toString());
		}
	}
	
	@Override public void printHIR() {
		for (CuProgr pr : elements) 
			pr.printHIR();
		System.out.println(s.toString());
	}
	
	@Override public void buildCFG()  {		
		for (CuProgr pr : elements) {
			if (pr instanceof ClassPrg) {
				//TO Do, probably never do
			}
			else if (pr instanceof FunPrg) {
				pr.buildCFG();
			}
			else {
				statements.add(((StatPrg)pr).stat);
			}
		}
		statements.add(s);
		super.entry = statements.get(0).getFirst();
		//the same way as dealing with stats
		CuStat temp = new Stats(statements);
		temp.buildCFG();
	}
	
	@Override public void buildSets() {
		
		//first build the use def sets
		//the same way as dealing with stats
		CuStat temp = new Stats(statements);
		temp.setUseDef();
		
		//next, iteratively build the in out sets
		nodes = Helper.buildSet(entry);
		//dead code elimination
		boolean nothingDies = false;
		while (!nothingDies) {
			nothingDies = true;
			for (CuStat cs : nodes) {
				cs.resetInOutSet();
			}
			nodes = Helper.buildSet(entry);
			for (CuStat cs : nodes) {
				if (cs.dies())
					nothingDies = false;
			}
		}
		
		for (CuProgr pr : elements) {
			if (pr instanceof ClassPrg) {
				//TO Do, probably never do
			}
			else if (pr instanceof FunPrg) {
				((FunPrg) pr).statement.setUseDef();
				pr.nodes = Helper.buildSet(pr.entry);
				nothingDies = false;
				while (!nothingDies) {
					nothingDies = true;
					for (CuStat cs : pr.nodes) {
						cs.resetInOutSet();
					}
					pr.nodes = Helper.buildSet(pr.entry);
					for (CuStat cs : pr.nodes) {
						if (cs.dies())
							nothingDies = false;
					}
				}
				
				//if the argument variable is not in the in set, put it in the in set
				for (String str : ((FunPrg) pr).typeScheme.data_tc.keySet()) {
					if (!pr.entry.inV.contains(str)) {
						pr.entry.inV.add(str);
					}
				}
			}
		}
	}
	
	@Override public String toC(ArrayList<String> localVars) {
		String fnClass_str = "", temp_str = "";
		for (CuProgr cp : elements) {
			if (cp instanceof ClassPrg || cp instanceof FunPrg) {
				fnClass_str += cp.toC(localVars);
			} 
			else {				
				temp_str += cp.toC(localVars);
				for (String str : cp.newVars) {
					if (!super.newVars.contains(str)) {
						super.newVars.add(str);
					}
				}
			}
		}
		//need to call toC first before using newVars		
		temp_str += s.toC(localVars);
		for(String str : s.newVars) {
			if (!super.newVars.contains(str)) {
				super.newVars.add(str);
			}
		}
		
		super.ctext = 
				  "#include \"cubex_main.h\"\n"
				+ "#include \"cubex_external_functions.h\"\n"
				+ "#include \"cubex_lib.h\"\n\n"
				+  fnClass_str + "\n\n"
				+ "void* our_main()\n{\n";
		
    	for (String str : super.newVars) {
    		super.ctext += "void * " + str + " = NULL;\n";
    		temp_str = temp_str.replaceAll("void \\* " + str + " = NULL;\n", "");
    	}
    	
    	super.ctext += temp_str;
    	
    	super.ctext += "}\n\n\n"
    			+ "void cubex_main() {\n"
				+ "Iterable* ourMain, *temp;\n"
				+ "ourMain = (Iterable*) our_main();\n"
				+ "temp = ourMain;\n"
				+ "while(ourMain != NULL) {\n\t"
				+ "print_line(((String*)ourMain->value)->value, ((String*)ourMain->value)->len);\n\t"
				+ "ourMain = iterGetNext(ourMain);\n}\n"
				+ "freeIter(temp);\n}";
				
    	
		return super.ctext;
	}
	public void calculateType(CuContext context) throws NoSuchTypeException {
		int i = 0;
		for (CuProgr prg : elements) { 
			//System.out.println(prg.toString());
		}
		for (CuProgr prg1 : elements) {
			//System.out.println(i + ":" + prg1.toString());
			i++;
			prg1.calculateType(context);
			//System.out.println("finished one");
		}
		HReturn re = this.s.calculateType(context);
Helper.P("program return, type is " + re.tau.toString());
		if (!re.tau.isSubtypeOf(new Iter(CuType.string)) || (re.b== false)) {
			throw new NoSuchTypeException(Helper.getLineInfo());
		}
	}
}

class ClassPrg extends CuProgr {
	CuClass c; 
	public ClassPrg (CuClass c) {
		//System.out.println("in class program constructor");
		this.c = c;
		super.text = c.toString();
		//System.out.println("exit class program constructor");
	}
	@Override public void calculateType(CuContext context) throws NoSuchTypeException {
		//Yinglei's guess it that depends on its detailed type (class/interface),
		//it will use the correct function and change the context properly
		//System.out.println("in class program");
		context.mergeVariable();
		c.calculateType(context);
		//System.out.println("in class program, end");
	}
	public String toC(ArrayList<String> localVars) {
		return c.toC();
	}
}

class FunPrg extends CuProgr {
	//used for printing in project 2
	List<String> fun; // need to change
	//used for type checking in project 3
	String name;
	CuTypeScheme typeScheme;
	CuStat statement;

	public FunPrg(CuVvc var, CuTypeScheme ts, CuStat s) {
		//System.out.println("in fun program constructor, begin");
		//String t = String.format("fun %s %s %s", var.toString(), ts.toString(), s.toString());
		//fun.add(t);
		this.name = var.toString();
		this.typeScheme = ts;
		this.statement = s;
		//System.out.println("in fun program constructor, end");
	}
	
	@Override public void toHIR() {
		this.statement = this.statement.toHIR();
		if (Helper.debug) {
			System.out.println("fun " + name + this.typeScheme.toString() + "{\n");
			System.out.println(this.statement.toString());
			System.out.println("}\n");
		}
	}
	
	@Override public void printHIR() {
		System.out.println("fun " + name + this.typeScheme.toString() + "{\n");
		System.out.println(this.statement.toString());
		System.out.println("}\n");
	}
	
	@Override public void buildCFG() {
		super.entry = this.statement.getFirst();
		this.statement.buildCFG();
	}
	
	@Override public void setUseDef() {
		this.statement.setUseDef();
	}
	
	@Override public String toString() {
		return Helper.printList("", fun, "", "");
	}
	@Override  public void calculateType(CuContext context) throws NoSuchTypeException {
Helper.P("in func program " + name);
		//System.out.println(this.statement.toString());
		//update the function context
		context.mergeVariable();
		if (context.mFunctions.containsKey(this.name)) {
			throw new NoSuchTypeException(Helper.getLineInfo());
		}
		context.updateFunction(this.name, this.typeScheme);
		//type check typeschemes and statements
		this.typeScheme.calculateType(context);
		CuContext temp_context = new CuContext (context);
		temp_context.updateKc(this.typeScheme.data_kc);
		for(String cur_str : this.typeScheme.data_tc.keySet())
		{
			if(context.mVariables.containsKey(cur_str)){
				throw new NoSuchTypeException(Helper.getLineInfo());
			}
		}
		temp_context.updateMutType(this.typeScheme.data_tc);
		HReturn re = this.statement.calculateType(temp_context);
		if (re.b == false) {
			throw new NoSuchTypeException(Helper.getLineInfo());
		}
		if (!re.tau.isSubtypeOf(this.typeScheme.data_t)) {
			throw new NoSuchTypeException(Helper.getLineInfo());
		}
	}
	@SuppressWarnings("unchecked")
	public String toC(ArrayList<String> localVars){
		StringBuilder sb= new StringBuilder();
		StringBuilder inputs=new StringBuilder();
		Helper.cFunType.put(name, typeScheme.data_t.id);
		ArrayList<String> local=new ArrayList<String>();
		Helper.funArgList	   =(ArrayList<String>) localVars.clone();
		
		sb.append("void* "+name.toString()+"(");
		String delim = "";
		for (Entry<String, CuType> e : typeScheme.data_tc.entrySet()){
			inputs.append(delim).append("void *"+e.getKey());
			delim=" , ";
			Helper.cVarType.put(e.getKey(), e.getValue().id);
			Helper.funArgList.add(e.getKey());
		}
		sb.append(inputs);
		sb.append(") {\n");
		for (String str : this.typeScheme.data_tc.keySet()) {
			sb.append(Helper.incrRefCount(str));
		}
		sb.append(statement.toC(local));
		sb.append("}\n");
		//added by Yinglei, when we get out of function, should clear this because this is 
		//a global list
		Helper.funArgList = new ArrayList<String>();
		return sb.toString();
	}

}

class StatPrg extends CuProgr {
	public CuStat stat;
	public StatPrg(CuStat s) {
		//System.out.println("in statement program constructor");
		this.stat = s;
		super.text = s.toString();
	}
	@Override public void toHIR() {
		stat = stat.toHIR();
		if (Helper.debug) {
			System.out.println(stat.toString());
		}
	}
	@Override public void printHIR() {
		System.out.println(stat.toString());
	}
	@Override public String toC(ArrayList<String> localVars) {
		super.ctext = stat.toC(localVars);
		super.newVars = stat.newVars;
		return super.ctext;
	}
	@Override public void calculateType(CuContext context) throws NoSuchTypeException {
		//System.out.println("in statement program");
		HReturn re = stat.calculateType(context);
		//System.out.println("before checking return type, type is "+re.tau.id);
		CuType dtype= new Iter(CuType.string);
		//System.out.println("created type iterable of string ");
		//final tau should be a subtype of iterable string
		if (!re.tau.isSubtypeOf(dtype)) {
			//System.out.println("type is not iterable of string");
			throw new NoSuchTypeException(Helper.getLineInfo());
		}
	}
	
}
