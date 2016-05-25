/* Pi computation using Chudnovsky's algorithm.

 * Copyright 2002, 2005 Hanhong Xue (macroxue at yahoo dot com)

 * Slightly modified 2005 by Torbjorn Granlund to allow more than 2G
   digits to be computed.
   
 * 2016: Ported to Java, using GMP through JNI, by Andy Quick.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO
 * EVENT SHALL THE AUTHORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.gmplib.test.pi;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.gmplib.gmpjni.GMP;
import org.gmplib.gmpjni.GMP.mpz_t;
import org.gmplib.gmpjni.GMP.mpf_t;
import org.gmplib.gmpjni.GMP.GMPException;
import org.gmplib.gmpjni.GMP.MutableInteger;
//import org.gmplib.gmpjni.GMP.randstate_t;

public class PI_Task extends AsyncTask<Integer, Integer, Integer>
{

    private static final String TAG = "PI_Task";
    private static final long A =  13591409;
    private static final long B =  545140134;
    private static final long C =  640320;
    private static final long D =  12;

    private static final double BITS_PER_DIGIT  =  3.32192809488736234787;
    private static final double DIGITS_PER_ITER =  14.1816474627254776555;
    private static final int DOUBLE_PREC =     53;

    private String result;
    private UI uinterface;
    private Runtime rt;
    
    private mpf_t t1;
    private mpf_t t2;
    
    public PI_Task(UI ui)
        throws GMPException
    {
	uinterface = ui;
	ftmp = new fac_t();
	fmul = new fac_t();
	out = 1; // 3;
	result = null;
	rt = Runtime.getRuntime();
    }

    private static long cputime()
    {
	return System.currentTimeMillis();
    }
    
    private void check_mem_usage()
    {
	try {
	    Process proc = rt.exec(new String[] {"/system/bin/ps"});
	    proc.waitFor();
	    BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	    String line;
	    for (;;) {
		line = br.readLine();
		if (line == null) break;
		if (line.length() > 0) {
		    Log.d(TAG, line);
		    break;
		}
	    }
	    for (;;) {
		line = br.readLine();
		if (line == null) break;
		if (line.indexOf("org.gmplib.test.pi") >= 0) {
		    Log.d(TAG, line);
		    break;
		}
	    }
	}
	catch (IOException e) {
	}
	catch (InterruptedException e) {
	}
    }
    
    /* r = sqrt(x) */
    private void my_sqrt_ui(mpf_t r, long x)
        throws GMPException
    {
        long prec, bits, prec0;

        prec0 = GMP.mpf_get_prec(r);

        if (prec0<=DOUBLE_PREC) {
            GMP.mpf_set_d(r, Math.sqrt((double)x));
            return;
        }

        bits = 0;
        for (prec=prec0; prec>DOUBLE_PREC;) {
            int bit = (int)(prec & 1);
            prec = (prec+bit)/2;
            bits = bits*2+bit;
        }

        GMP.mpf_set_prec_raw(t1, DOUBLE_PREC);
        GMP.mpf_set_d(t1, 1.0/Math.sqrt((double)x));

        while (prec<prec0) {
            prec *=2;
            if (prec<prec0) {
                /* t1 = t1+t1*(1-x*t1*t1)/2; */
                GMP.mpf_set_prec_raw(t2, prec);
                GMP.mpf_mul(t2, t1, t1);         /* half x half -> full */
                GMP.mpf_mul_ui(t2, t2, x);
                GMP.mpf_ui_sub(t2, 1, t2);
                GMP.mpf_set_prec_raw(t2, prec/2);
                GMP.mpf_div_2exp(t2, t2, 1);
                GMP.mpf_mul(t2, t2, t1);         /* half x half -> half */
                GMP.mpf_set_prec_raw(t1, prec);
                GMP.mpf_add(t1, t1, t2);
            } else {
                break;
            }
            prec -= (bits&1);
            bits /=2;
        }
        /* t2=x*t1, t1 = t2+t1*(x-t2*t2)/2; */
        GMP.mpf_set_prec_raw(t2, prec0/2);
        GMP.mpf_mul_ui(t2, t1, x);
        GMP.mpf_mul(r, t2, t2);          /* half x half -> full */
        GMP.mpf_ui_sub(r, x, r);
        GMP.mpf_mul(t1, t1, r);          /* half x half -> half */
        GMP.mpf_div_2exp(t1, t1, 1);
        GMP.mpf_add(r, t1, t2);
    }
    
    private static class fac_t
    {
	public int max_facs;
	public int num_facs;
	public int[] fac;
	public int[] pow;
    }

    private static class sieve_t
    {
	public int fac;
	public int pow;
	public int nxt;
    };

    private sieve_t[] sieve;
    private int sieve_size;
    private fac_t ftmp;
    private fac_t fmul;

    private static final int INIT_FACS = 32;
    
    private static int min(int x, int y) { return (x < y ? x : y);}
    private static int max(int x, int y) { return (x > y ? x : y);}

    private static void fac_copy(fac_t dst, fac_t src)
    {
	dst.max_facs = src.max_facs;
	dst.num_facs = src.num_facs;
	dst.fac = src.fac;
	dst.pow = src.pow;
    }
    
    private static void fac_swap(fac_t f, fac_t g)
    {
	int t_max_facs;
	int t_num_facs;
	int[] t_fac;
	int[] t_pow;
	
	t_max_facs = f.max_facs;
	t_num_facs = f.num_facs;
	t_fac = f.fac;
	t_pow = f.pow;
	f.max_facs = g.max_facs;
	f.num_facs = g.num_facs;
	f.fac = g.fac;
	f.pow = g.pow;
	g.max_facs = t_max_facs;
	g.num_facs = t_num_facs;
	g.fac = t_fac;
	g.pow = t_pow;
    }
    
    private static void fac_show(String prefix, fac_t f)
    {
	StringBuffer sb = new StringBuffer();
        int i;
        for (i=0; i<f.num_facs; i++) {
            if (f.pow[i]==1) {
                sb.append(f.fac[i]);
                sb.append(' ');
            } else {
                sb.append(f.fac[i]);
                sb.append("^");
                sb.append(f.pow[i]);
                sb.append(' ');
            }
        }
        Log.d(TAG, prefix + sb.toString());
    }

    private static void fac_reset(fac_t f)
    {
        f.num_facs = 0;
    }

    private static void fac_init_size(fac_t f, int s)
    {
        if (s<INIT_FACS) {
            s=INIT_FACS;
        }

        f.fac  = new int[s]; // malloc(s*sizeof(unsigned long)*2);
        f.pow  = new int[s]; // f[0].pow  = f[0].fac + s;
        f.max_facs = s;

        fac_reset(f);
    }

    private static void fac_init(fac_t f)
    {
        fac_init_size(f, INIT_FACS);
    }

    private static void fac_clear(fac_t f)
    {
        f.fac = null;
        f.pow = null;
    }

    private static void fac_resize(fac_t f, int s)
    {
        if (f.max_facs < s) {
            fac_clear(f);
            fac_init_size(f, s);
        }
    }

    /* f = base^pow */
    private void fac_set_bp(fac_t f, int base, int pow)
    {
        int i;
        assert(base<sieve_size);
        for (i=0, base/=2; base>0; i++, base = sieve[base].nxt) {
            f.fac[i] = sieve[base].fac;
            f.pow[i] = sieve[base].pow*pow;
        }
        f.num_facs = i;
        assert(i<=f.max_facs);
    }

    /* r = f*g */
    private static void fac_mul2(fac_t r, fac_t f, fac_t g)
    {
        int i, j, k;

        for (i=j=k=0; i<f.num_facs && j<g.num_facs; k++) {
            if (f.fac[i] == g.fac[j]) {
                r.fac[k] = f.fac[i];
                r.pow[k] = f.pow[i] + g.pow[j];
                i++; j++;
            } else if (f.fac[i] < g.fac[j]) {
                r.fac[k] = f.fac[i];
                r.pow[k] = f.pow[i];
                i++;
            } else {
                r.fac[k] = g.fac[j];
                r.pow[k] = g.pow[j];
                j++;
            }
        }
        for (; i<f.num_facs; i++, k++) {
            r.fac[k] = f.fac[i];
            r.pow[k] = f.pow[i];
        }
        for (; j<g.num_facs; j++, k++) {
            r.fac[k] = g.fac[j];
            r.pow[k] = g.pow[j];
        }
        r.num_facs = k;
        assert(k<=r.max_facs);
    }

    /* f *= g */
    private void fac_mul(fac_t f, fac_t g)
    {
        fac_resize(fmul, f.num_facs + g.num_facs);
        fac_mul2(fmul, f, g);
        fac_swap(f, fmul);
    }

    /* f *= base^pow */
    private void fac_mul_bp(fac_t f, int base, int pow)
    {
        fac_set_bp(ftmp, base, pow);
        fac_mul(f, ftmp);
    }

    /* remove factors of power 0 */
    private static void fac_compact(fac_t f)
    {
        int i, j;
        for (i=0, j=0; i<f.num_facs; i++) {
            if (f.pow[i]>0) {
                if (j<i) {
                    f.fac[j] = f.fac[i];
                    f.pow[j] = f.pow[i];
                }
                j++;
            }
        }
        f.num_facs = j;
    }

    /* convert factorized form to number */
    private void bs_mul(mpz_t r, int a, int b)
        throws GMPException
    {
        int i, j;
        if (b-a<=32) {
            GMP.mpz_set_ui(r, 1);
            for (i=a; i<b; i++) {
                for (j=0; j<fmul.pow[i]; j++) {
                    GMP.mpz_mul_ui(r, r, fmul.fac[i]);
                }
            }
        } else {
            mpz_t r2 = new mpz_t();
            bs_mul(r2, a, (a+b)/2);
            bs_mul(r, (a+b)/2, b);
            GMP.mpz_mul(r, r, r2);
        }
    }

    private mpz_t    gcd;

    /* f /= gcd(f,g), g /= gcd(f,g) */
    private void fac_remove_gcd(mpz_t p, fac_t fp, mpz_t g, fac_t fg)
        throws GMPException
    {
        int i, j, k, c;
        fac_resize(fmul, min(fp.num_facs, fg.num_facs));
        for (i=j=k=0; i<fp.num_facs && j<fg.num_facs; ) {
            if (fp.fac[i] == fg.fac[j]) {
                c = min(fp.pow[i], fg.pow[j]);
                fp.pow[i] -= c;
                fg.pow[j] -= c;
                fmul.fac[k] = fp.fac[i];
                fmul.pow[k] = c;
                i++; j++; k++;
            } else if (fp.fac[i] < fg.fac[j]) {
                i++;
            } else {
                j++;
            }
        }
        fmul.num_facs = k;
        assert(k <= fmul.max_facs);

        if (fmul.num_facs != 0) {
            bs_mul(gcd, 0, fmul.num_facs);
            GMP.mpz_divexact(p, p, gcd);
            GMP.mpz_divexact(g, g, gcd);
            fac_compact(fp);
            fac_compact(fg);
        }
    }

    int out;
    private mpz_t[]   pstack;
    private mpz_t[]   qstack;
    private mpz_t[]   gstack;
    private fac_t[]   fpstack;
    private fac_t[]   fgstack;
    private int       top;
    private int       progress;
    private int       progresspct;
    private double    percent;

    /* binary splitting */
    private void bs(int a, int b, boolean gflag, int level)
        throws GMPException
    {
        int i, mid;
        boolean ccc;
        mpz_t p1;
        mpz_t q1;
        mpz_t g1;
        fac_t fp1;
        fac_t fg1;

        if (b-a==1) {
            /*
              g(b-1,b) = (6b-5)(2b-1)(6b-1)
              p(b-1,b) = b^3 * C^3 / 24
              q(b-1,b) = (-1)^b*g(b-1,b)*(A+Bb).
            */
            p1 = (pstack[top]);
            q1 = (qstack[top]);
            g1 = (gstack[top]);
            fp1 = (fpstack[top]);
            fg1 = (fgstack[top]);

            GMP.mpz_set_ui(p1, b);
            GMP.mpz_mul_ui(p1, p1, b);
            GMP.mpz_mul_ui(p1, p1, b);
            GMP.mpz_mul_ui(p1, p1, (C/24)*(C/24));
            GMP.mpz_mul_ui(p1, p1, C*24);

            GMP.mpz_set_ui(g1, 2*b-1);
            GMP.mpz_mul_ui(g1, g1, 6*b-1);
            GMP.mpz_mul_ui(g1, g1, 6*b-5);

            GMP.mpz_set_ui(q1, b);
            GMP.mpz_mul_ui(q1, q1, B);
            GMP.mpz_add_ui(q1, q1, A);
            GMP.mpz_mul   (q1, q1, g1);
            if (b%2 != 0) {
                GMP.mpz_neg(q1, q1);
            }

            i=(int)b;
            while ((i&1)==0) i>>=1;
            fac_set_bp(fp1, i, 3);  /*  b^3 */
            fac_mul_bp(fp1, 3*5*23*29, 3);
            fp1.pow[0]--;

            fac_set_bp(fg1, 2*b-1, 1);  /* 2b-1 */
            fac_mul_bp(fg1, 6*b-1, 1);  /* 6b-1 */
            fac_mul_bp(fg1, 6*b-5, 1);  /* 6b-5 */

            if (b > progress) {
                //printf("."); fflush(stdout);
                progress = b;
                int pct = (int)((double)progress*percent);
                if (pct > progresspct) {
                    progresspct = pct;
                    publishProgress(progresspct);
                }
            }

        } else {
            /*
              p(a,b) = p(a,m) * p(m,b)
              g(a,b) = g(a,m) * g(m,b)
              q(a,b) = q(a,m) * p(m,b) + q(m,b) * g(a,m)
            */
            mid = (int)((double)a+(double)(b-a)*0.5224);     /* tuning parameter */
            bs(a, mid, true, level+1);

            top++;
            bs(mid, b, gflag, level+1);
            top--;

            p1 = (pstack[top]);
            q1 = (qstack[top]);
            g1 = (gstack[top]);
            fp1 = (fpstack[top]);
            fg1 = (fgstack[top]);
            mpz_t p2 = (pstack[top+1]);
            mpz_t q2 = (qstack[top+1]);
            mpz_t g2 = (gstack[top+1]);
            fac_t fp2 = (fpstack[top+1]);
            fac_t fg2 = (fgstack[top+1]);

            /***
            if (level == 0) {
                puts ("");
            }
            ***/

            ccc = (level == 0) && ((out&2) != 0);

            if (ccc) {
        	check_mem_usage();
            }

            if (level>=4) {           /* tuning parameter */
                fac_remove_gcd(p2, fp2, g1, fg1);
            }

            if (ccc) {
        	check_mem_usage();
            }
            GMP.mpz_mul(p1, p1, p2);

            if (ccc) {
        	check_mem_usage();
            }
            GMP.mpz_mul(q1, q1, p2);

            if (ccc) {
        	check_mem_usage();
            }
            GMP.mpz_mul(q2, q2, g1);

            if (ccc) {
        	check_mem_usage();
            }
            GMP.mpz_add(q1, q1, q2);

            if (ccc) {
        	check_mem_usage();
            }
            fac_mul(fp1, fp2);

            if (gflag) {
                GMP.mpz_mul(g1, g1, g2);
                fac_mul(fg1, fg2);
            }
        }

        /***
        if (out&2 != 0) {
            printf("p(%ld,%ld)=",a,b); fac_show(fp1);
            if (gflag)
                printf("g(%ld,%ld)=",a,b); fac_show(fg1);
        }
        ***/
        if ((out&2) != 0) {
            fac_show("p(" + a + ", " + b + ")=", fp1);
            if (gflag) {
                fac_show("g(" + a + ", " + b + ")=", fg1);
            }
        }
    }
    
    private void dump_sieve(int n, sieve_t[] s)
    {
	int i;
	Log.d(TAG, "----- sieve -------");
	i = n/2 - 1;
	for (i = 0; i < n/2; i++) {
	    Log.d(TAG, "" + i + ": " + s[i].fac + ", " + s[i].pow + ", " + s[i].nxt);
	}
	Log.d(TAG, "------------------");
    }

    private void build_sieve(int n, sieve_t[] s)
    {
        int m, i, j, k;

        sieve_size = n;
        m = (int)Math.sqrt((double)n);
        for (i = 0; i < n/2; i++) {
            s[i] = new sieve_t();
            s[i].fac = 0;
            s[i].pow = 0;
            s[i].nxt = 0;
        }
        //memset(s, 0, sizeof(sieve_t)*n/2);

        s[1/2].fac = 1;
        s[1/2].pow = 1;

        for (i=3; i<=n; i+=2) {
            if (s[i/2].fac == 0) {
                s[i/2].fac = i;
                s[i/2].pow = 1;
                if (i<=m) {
                    for (j=i*i, k=i/2; j<=n; j+=i+i, k++) {
                        if (s[j/2].fac==0) {
                            s[j/2].fac = i;
                            if (s[k].fac == i) {
                                s[j/2].pow = s[k].pow + 1;
                                s[j/2].nxt = s[k].nxt;
                            } else {
                                s[j/2].pow = 1;
                                s[j/2].nxt = k;
                            }
                        }
                    }
                }
            }
        }
    }

    protected Integer doInBackground(Integer... params)
    {
        int rc = -1;
	try {
	    mpf_t  pi;
	    mpf_t  qi;
	    int d=100;
	    int i;
	    int depth=1;
	    int terms;
	    long psize;
	    long qsize;
	    long begin;
	    long mid0;
	    long mid1;
	    long mid2;
	    long mid3;
	    long mid4;
	    long mid5;
	    long end;
	    String str;
	    StringBuffer resultBuffer = new StringBuffer();
	    MutableInteger exp = new MutableInteger(0);

	    //prog_name = argv[0];

	    /***
	    if (argc>1)
	        d = strtoul(argv[1], 0, 0);
	    if (argc>2)
	        out = atoi(argv[2]);
	    ***/
            if (params.length > 0) {
                d = params[0].intValue();
            }
            if (params.length > 1) {
                out = params[1].intValue();
            }

	    terms = (int)((double)d/DIGITS_PER_ITER);
	    while ((1L<<depth)<terms) {
	        depth++;
	    }
	    depth++;
	    percent = 100.0/(double)terms;
	    progress = 0;
	    progresspct = 0;
	    Log.d(TAG, "#terms=" + terms + ", depth=" + depth);

	    begin = cputime();
	    //printf("sieve   "); fflush(stdout);

	    sieve_size = max(3*5*23*29+1, terms*6);
	    //sieve = (sieve_t *)malloc(sizeof(sieve_t)*sieve_size/2);
	    sieve = new sieve_t[sieve_size/2];
	    build_sieve(sieve_size, sieve);
	    if ((out&2) != 0) {
	        dump_sieve(sieve_size, sieve);
	    }

	    mid0 = cputime();
	    Log.d(TAG, "sieve: time = " + (mid0-begin) + " milliseconds");

	    /* allocate stacks */
	    pstack = new mpz_t[depth];
	    qstack = new mpz_t[depth];
	    gstack = new mpz_t[depth];
	    fpstack = new fac_t[depth];
	    fgstack = new fac_t[depth];
	    for (i=0; i<depth; i++) {
	        pstack[i] = new mpz_t();
	        qstack[i] = new mpz_t();
	        gstack[i] = new mpz_t();
	        fpstack[i] = new fac_t();
	        fgstack[i] = new fac_t();
	        fac_init(fpstack[i]);
	        fac_init(fgstack[i]);
	    }
	    gcd = new mpz_t();
	    fac_init(ftmp);
	    fac_init(fmul);

	    top = 0;
	    mpz_t p1 = (pstack[top]);
	    mpz_t q1 = (qstack[top]);
	    mpz_t g1 = (gstack[top]);
	    mpz_t p2 = (pstack[top+1]);
	    mpz_t q2 = (qstack[top+1]);
	    mpz_t g2 = (gstack[top+1]);
	    /* begin binary splitting process */
	    if (terms<=0) {
	        GMP.mpz_set_ui(p2,1);
	        GMP.mpz_set_ui(q2,0);
	        GMP.mpz_set_ui(g2,1);
	    } else {
	        bs(0,terms,false,0);
	    }

	    mid1 = cputime();
	    Log.d(TAG, "bs:      time = " + (mid1-mid0) + " milliseconds");
	    //printf("   gcd  time = %6.3f\n", (double)(gcd_time)/1000);

	    /* printf("misc    "); fflush(stdout); */

	    /* free some resources */
	    //free(sieve);
	    sieve = null;

	    gcd = null;
	    fac_clear(ftmp);
	    fac_clear(fmul);

	    for (i=1; i<depth; i++) {
	        pstack[i] = null;
	        qstack[i] = null;
	        gstack[i] = null;
	        fac_clear(fpstack[i]);
	        fac_clear(fgstack[i]);
	    }

	    gstack[0] = null;
	    fac_clear(fpstack[0]);
	    fac_clear(fgstack[0]);

	    gstack = null;
	    fpstack = null;
	    fgstack = null;

	    /* prepare to convert integers to floats */
	    GMP.mpf_set_default_prec((long)((double)d*BITS_PER_DIGIT+16));

	    /*
	         p*(C/D)*sqrt(C)
	    pi = -----------------
	         (q+A*p)
	    */

	    psize = GMP.mpz_sizeinbase(p1,10);
	    qsize = GMP.mpz_sizeinbase(q1,10);

	    GMP.mpz_addmul_ui(q1, p1, A);
	    GMP.mpz_mul_ui(p1, p1, C/D);

	    pi = new mpf_t();
	    GMP.mpf_set_z(pi, p1);
	    p1 = null;

	    qi = new mpf_t();
	    GMP.mpf_set_z(qi, q1);
	    q1 = null;

	    pstack = null;
	    qstack = null;

	    mid2 = cputime();
	    Log.d(TAG, "init: time = " + (mid2-mid1) + " milliseconds");

	    /* initialize temp float variables for sqrt & div */
	    t1 = new mpf_t();
            t2 = new mpf_t();
	    /* mpf_set_prec_raw(t1, mpf_get_prec(pi)); */

	    /* final step */
	    //printf("div     ");  fflush(stdout);
	    GMP.mpf_div(qi, pi, qi);
	    mid3 = cputime();
	    Log.d(TAG, "div: time = " + (mid3-mid2) + " milliseconds");

	    //printf("sqrt    ");  fflush(stdout);
	    my_sqrt_ui(pi, C);
	    mid4 = cputime();
	    Log.d(TAG, "sqrt: time = " + (mid4-mid3) + " milliseconds");
	    str = GMP.mpf_get_str(exp, 10, d+2, pi);
	    if ((out&1) != 0) {
		Log.d(TAG, "sqrt(C)=0." + str + "E" + exp.value);
	    }

	    //printf("mul     ");  fflush(stdout);
	    GMP.mpf_mul(qi, qi, pi);
	    mid5 = cputime();
	    Log.d(TAG, "mul: time = " + (mid5-mid4) + " milliseconds");

	    //fflush(stdout);

	    Log.d(TAG, "P size=" + psize + " digits (" + (double)psize/(double)d + ")" +
	        "   Q size=" + qsize + " digits (" + (double)qsize/(double)d + ")");

	    /* output Pi and timing statistics */
	    /***
	    if (out&1)  {
	        printf("pi(0,%ld)=\n", terms);
	        mpf_out_str(stdout, 10, d+2, qi);
	        printf("\n");
	    }
	    ***/
	    resultBuffer.setLength(0);
	    str = GMP.mpf_get_str(exp, 10, d+2, qi);
	    resultBuffer.append("0.");
	    resultBuffer.append(str);
	    resultBuffer.append("E");
	    resultBuffer.append(Integer.toString(exp.value));
	    
	    end = cputime();
	    Log.d(TAG, "convert: time = " + (end-mid5) + " milliseconds");
	    Log.d(TAG, "total   time = " + (end-begin) + " milliseconds");
	    
	    if ((out&1) != 0) {
		Log.d(TAG, "qi(0," + terms + ")=0." + str + "E" + exp.value);
	    }
	    GMP.mpf_sub(t1, (mpf_t)uinterface.getRef(), qi);
	    GMP.mpf_abs(t1,  t1);
	    GMP.mpf_get_str(exp, 10, d+2, t1);
	    if ((out&1) != 0) {
		Log.d(TAG, "|ref value - computed value| < 1E" + exp.value);
	    }
	    resultBuffer.append("\n\nError < 1E");
	    resultBuffer.append(Integer.toString(exp.value));
	    result = resultBuffer.toString();

	    /* free float resources */
	    pi = null;
	    qi = null;

	    t1 = null;
	    t2 = null;
	    rc = 0;
	}
	catch (GMPException e) {
	    rc = -1;
	}
        return Integer.valueOf(rc);
    }

    protected void onPostExecute(Integer result)
    {
	uinterface.display(this.result);
    }
    
    protected void onPreExecute()
    {
        uinterface.display(TAG);
    }

    protected void onProgressUpdate(Integer... progress)
    {
        uinterface.display("progress=" + progress[0]);
    }

    public String getResult()
    {
	return result;
    }
    
}
