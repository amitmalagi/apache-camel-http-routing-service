package com.isha.routes;

public class SigninCredentials {
	
	private String email;
	private String passwd;
	private String vendor;
	
	public SigninCredentials() {
		super();
		// TODO Auto-generated constructor stub
	}

	public SigninCredentials(String email, String passwd, String vendor) {
		super();
		this.email = email;
		this.passwd = passwd;
		this.vendor = vendor;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswd() {
		return passwd;
	}

	public void setPasswd(String passwd) {
		this.passwd = passwd;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}
	
	
	

}
